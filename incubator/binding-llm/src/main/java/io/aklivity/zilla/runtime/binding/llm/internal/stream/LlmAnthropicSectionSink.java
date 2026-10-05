/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.engine.util.Flags;

final class LlmAnthropicSectionSink implements JsonSink
{

    private static final String BLOCK_TOOL_USE = "tool_use";
    private static final String BLOCK_CONTENT = "content";
    private static final String BLOCK_CONTENT_BLOCK = "content_block";

    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private final Output output;
    private final StringBuilder path;
    private final int[] pathLengthAt;
    private final StringBuilder input;

    private int depth;
    private String pendingKey;

    private String blockType;
    private String toolId;
    private String toolName;

    private int inputDepth;
    private boolean inputOpen;
    private boolean inputSeparator;
    private boolean inputStringOpen;

    private boolean sectionOpen;
    private boolean sectionStarted;
    private LlmSectionType sectionType;
    private String sectionToolCallId;
    private String sectionToolName;

    private char highSurrogate;
    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;

    LlmAnthropicSectionSink(
        Output output)
    {
        this.output = output;
        this.path = new StringBuilder();
        this.pathLengthAt = new int[16];
        this.input = new StringBuilder();
        this.textBytes = new byte[1024];
        this.textBuffer = new UnsafeBufferEx(textBytes);
    }

    void finish()
    {
        closeSection();
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            onContainerStart(event);
            break;
        case END_OBJECT:
        case END_ARRAY:
            status = onContainerEnd(event);
            break;
        case KEY_NAME:
            onKeyName(control, source);
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            onLeafValue(control, source, event);
            break;
        default:
            break;
        }

        return status;
    }

    @Override
    public void reset()
    {
        path.setLength(0);
        depth = 0;
        pendingKey = null;
        inputOpen = false;
        inputStringOpen = false;
        input.setLength(0);
        sectionOpen = false;
        sectionStarted = false;
        highSurrogate = 0;
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public boolean deterministic()
    {
        return true;
    }

    private void onContainerStart(
        JsonEvent event)
    {
        pathLengthAt[depth++] = path.length();

        if (inputOpen)
        {
            inputValueStart();
            input.append(event == JsonEvent.START_OBJECT ? '{' : '[');
            inputDepth++;
            inputSeparator = false;
            pendingKey = null;
        }
        else if (pendingKey != null)
        {
            if (path.length() > 0)
            {
                path.append('.');
            }
            path.append(pendingKey);

            if ("input".equals(pendingKey) && event == JsonEvent.START_OBJECT && (pathIs("content.input") ||
                pathIs("content_block.input")))
            {
                inputOpen = true;
                inputDepth = 1;
                inputSeparator = false;
                input.setLength(0);
                input.append('{');
            }

            pendingKey = null;
        }
        else if (event == JsonEvent.START_OBJECT && pathIs(BLOCK_CONTENT))
        {
            blockType = null;
            toolId = null;
            toolName = null;
        }
    }

    private Status onContainerEnd(
        JsonEvent event)
    {
        if (inputOpen)
        {
            input.append(event == JsonEvent.END_OBJECT ? '}' : ']');
            inputSeparator = true;

            if (--inputDepth == 0)
            {
                inputOpen = false;
                onInputComplete();
            }
        }
        else if (event == JsonEvent.END_OBJECT && pathIs(BLOCK_CONTENT_BLOCK) && BLOCK_TOOL_USE.equals(blockType))
        {
            openSection(LlmSectionType.TOOL_CALL, toolId, toolName);
            emit(0, false);
        }

        path.setLength(pathLengthAt[--depth]);

        return depth == 0 ? Status.COMPLETED : Status.ADVANCED;
    }

    private void onInputComplete()
    {
        if (pathIs("content.input") && BLOCK_TOOL_USE.equals(blockType))
        {
            closeSection();
            openSection(LlmSectionType.TOOL_CALL, toolId, toolName);
            emit(encode(input, true), true);
        }
    }

    private void onKeyName(
        JsonController control,
        JsonSource source)
    {
        if (source.deferredBytes())
        {
            control.consumed(0);
        }
        else if (inputOpen)
        {
            if (inputSeparator)
            {
                input.append(',');
            }
            input.append('"');
            appendEscaped(source.getStringView());
            input.append("\":");
            inputSeparator = false;
        }
        else
        {
            pendingKey = source.getString();
        }
    }

    private void onLeafValue(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        if (inputOpen)
        {
            onInputValue(control, source, event);
        }
        else if (pendingKey != null)
        {
            final boolean deferred = source.deferredBytes();
            final int fieldAt = path.length();

            if (fieldAt > 0)
            {
                path.append('.');
            }
            path.append(pendingKey);

            final String field = path.toString();

            path.setLength(fieldAt);

            if (event == JsonEvent.VALUE_STRING)
            {
                onString(control, source, field, deferred);
            }

            if (!deferred)
            {
                pendingKey = null;
            }
        }
    }

    private void onInputValue(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        final boolean deferred = source.deferredBytes();

        if (event == JsonEvent.VALUE_STRING)
        {
            if (!inputStringOpen)
            {
                inputValueStart();
                input.append('"');
                inputStringOpen = true;
            }

            final CharSequence chars = source.getStringView();
            appendEscaped(chars);
            control.consumed(chars.length());

            if (!deferred)
            {
                input.append('"');
                inputStringOpen = false;
                inputSeparator = true;
            }
        }
        else
        {
            inputValueStart();

            switch (event)
            {
            case VALUE_NUMBER:
                input.append(source.getStringView());
                break;
            case VALUE_TRUE:
                input.append("true");
                break;
            case VALUE_FALSE:
                input.append("false");
                break;
            default:
                input.append("null");
                break;
            }

            inputSeparator = true;
        }
    }

    private void inputValueStart()
    {
        if (inputSeparator && input.length() > 0 && input.charAt(input.length() - 1) != ':')
        {
            input.append(',');
        }
        inputSeparator = false;
    }

    private void appendEscaped(
        CharSequence chars)
    {
        for (int i = 0; i < chars.length(); i++)
        {
            final char c = chars.charAt(i);

            switch (c)
            {
            case '"':
                input.append("\\\"");
                break;
            case '\\':
                input.append("\\\\");
                break;
            case '\n':
                input.append("\\n");
                break;
            case '\r':
                input.append("\\r");
                break;
            case '\t':
                input.append("\\t");
                break;
            default:
                if (c < 0x20)
                {
                    input.append(String.format("\\u%04x", (int) c));
                }
                else
                {
                    input.append(c);
                }
                break;
            }
        }
    }

    private void onString(
        JsonController control,
        JsonSource source,
        String field,
        boolean deferred)
    {
        switch (field)
        {
        case "type":
            onEventType(source);
            break;
        case "content_block.type":
        case "content.type":
            if (!deferred)
            {
                blockType = source.getString();
            }
            else
            {
                control.consumed(0);
            }
            break;
        case "content_block.id":
        case "content.id":
            if (deferred)
            {
                control.consumed(0);
            }
            else
            {
                toolId = source.getString();
            }
            break;
        case "content_block.name":
        case "content.name":
            if (deferred)
            {
                control.consumed(0);
            }
            else
            {
                toolName = source.getString();
            }
            break;
        case "content.text":
            onText(control, source, LlmSectionType.ASSISTANT_TEXT, deferred, true);
            break;
        case "content.thinking":
            onText(control, source, LlmSectionType.ASSISTANT_REASONING, deferred, true);
            break;
        case "delta.text":
            onText(control, source, LlmSectionType.ASSISTANT_TEXT, deferred, false);
            break;
        case "delta.thinking":
            onText(control, source, LlmSectionType.ASSISTANT_REASONING, deferred, false);
            break;
        case "delta.partial_json":
            onArguments(control, source, deferred);
            break;
        case "id":
        case "model":
        case "message.id":
        case "message.model":
        case "stop_reason":
        case "delta.stop_reason":
        case "error.type":
        case "error.message":
            if (deferred)
            {
                control.consumed(0);
            }
            break;
        default:
            control.consumed(source.getStringView().length());
            break;
        }
    }

    private void onEventType(
        JsonSource source)
    {
        final CharSequence type = source.getStringView();

        if (matches(type, "content_block_stop") || matches(type, "message_delta") || matches(type, "message_stop"))
        {
            closeSection();
        }
    }

    private void onText(
        JsonController control,
        JsonSource source,
        LlmSectionType type,
        boolean deferred,
        boolean atomic)
    {
        final CharSequence chars = source.getStringView();
        final int length = chars.length();

        if (length > 0 || highSurrogate != 0 || !deferred && atomic && sectionOpen)
        {
            if (!sectionOpen || type != sectionType)
            {
                closeSection();
                openSection(type, null, null);
            }

            emit(encode(chars, !deferred), !deferred && atomic);
        }

        control.consumed(length);
    }

    private void onArguments(
        JsonController control,
        JsonSource source,
        boolean deferred)
    {
        final CharSequence chars = source.getStringView();
        final int length = chars.length();

        if (sectionOpen && sectionType == LlmSectionType.TOOL_CALL && (length > 0 || highSurrogate != 0))
        {
            emit(encode(chars, !deferred), false);
        }

        control.consumed(length);
    }

    private void openSection(
        LlmSectionType type,
        String toolCallId,
        String toolName)
    {
        sectionOpen = true;
        sectionStarted = false;
        sectionType = type;
        sectionToolCallId = toolCallId;
        sectionToolName = toolName;
    }

    private void closeSection()
    {
        if (sectionOpen && sectionStarted)
        {
            emit(0, true);
        }

        sectionOpen = false;
    }

    private void emit(
        int length,
        boolean fin)
    {
        if (sectionOpen)
        {
            final int flags = sectionStarted ? Flags.NONE : Flags.INIT;

            sectionStarted = true;
            sectionOpen = !fin;

            output.begin();
            output.frame(
                sectionType,
                0,
                sectionToolCallId,
                sectionToolName,
                fin ? Flags.fin(flags) : flags,
                textBuffer,
                0,
                length);
        }
    }

    private boolean pathIs(
        String value)
    {
        return path.length() == value.length() && path.indexOf(value) == 0;
    }

    private static boolean matches(
        CharSequence chars,
        String value)
    {
        boolean matches = chars.length() == value.length();

        for (int i = 0; matches && i < value.length(); i++)
        {
            matches = chars.charAt(i) == value.charAt(i);
        }

        return matches;
    }

    private int encode(
        CharSequence chars,
        boolean last)
    {
        final int length = chars.length();

        if (textBytes.length < length * 3 + 8)
        {
            textBytes = new byte[length * 3 + 8];
            textBuffer = new UnsafeBufferEx(textBytes);
        }

        int position = 0;
        int index = 0;

        if (highSurrogate != 0 && length > 0)
        {
            final char c = chars.charAt(0);
            if (Character.isLowSurrogate(c))
            {
                position = put(Character.toCodePoint(highSurrogate, c), position);
                index = 1;
            }
            else
            {
                position = put(REPLACEMENT_CHARACTER, position);
            }
            highSurrogate = 0;
        }

        while (index < length)
        {
            final char c = chars.charAt(index++);

            if (Character.isHighSurrogate(c))
            {
                if (index < length)
                {
                    final char d = chars.charAt(index);
                    if (Character.isLowSurrogate(d))
                    {
                        position = put(Character.toCodePoint(c, d), position);
                        index++;
                    }
                    else
                    {
                        position = put(REPLACEMENT_CHARACTER, position);
                    }
                }
                else if (last)
                {
                    position = put(REPLACEMENT_CHARACTER, position);
                }
                else
                {
                    highSurrogate = c;
                }
            }
            else if (Character.isLowSurrogate(c))
            {
                position = put(REPLACEMENT_CHARACTER, position);
            }
            else
            {
                position = put(c, position);
            }
        }

        if (last && highSurrogate != 0)
        {
            position = put(REPLACEMENT_CHARACTER, position);
            highSurrogate = 0;
        }

        return position;
    }

    private int put(
        int codePoint,
        int position)
    {
        int next = position;

        if (codePoint < 0x80)
        {
            textBytes[next++] = (byte) codePoint;
        }
        else if (codePoint < 0x800)
        {
            textBytes[next++] = (byte) (0xC0 | codePoint >> 6);
            textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }
        else if (codePoint < 0x10000)
        {
            textBytes[next++] = (byte) (0xE0 | codePoint >> 12);
            textBytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
            textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }
        else
        {
            textBytes[next++] = (byte) (0xF0 | codePoint >> 18);
            textBytes[next++] = (byte) (0x80 | codePoint >> 12 & 0x3F);
            textBytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
            textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }

        return next;
    }

    interface Output
    {
        void begin();

        void frame(
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length);
    }
}
