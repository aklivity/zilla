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

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.engine.util.Flags;

final class LlmOpenaiSectionSink implements JsonSink
{
    private static final String TYPE_ASSISTANT_TEXT = "assistant-text";
    private static final String TYPE_ASSISTANT_REFUSAL = "assistant-refusal";
    private static final String TYPE_TOOL_CALL = "tool-call";

    private static final int NO_INDEX = -1;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private final Output output;
    private final StringBuilder path;
    private final int[] pathLengthAt;

    private int depth;
    private String pendingKey;

    private int choiceIndex;
    private int toolIndex;
    private String toolId;
    private String toolName;
    private boolean toolArguments;

    private boolean sectionOpen;
    private boolean sectionStarted;
    private String sectionType;
    private int sectionMessage;
    private int sectionTool;
    private String sectionToolId;
    private String sectionExtension;
    private String sectionAttributes;

    private char highSurrogate;
    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;

    LlmOpenaiSectionSink(
        Output output)
    {
        this.output = output;
        this.path = new StringBuilder();
        this.pathLengthAt = new int[16];
        this.textBytes = new byte[1024];
        this.textBuffer = new UnsafeBufferEx(textBytes);
        this.sectionTool = NO_INDEX;
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
        sectionOpen = false;
        sectionStarted = false;
        sectionTool = NO_INDEX;
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

        if (pendingKey != null)
        {
            if (path.length() > 0)
            {
                path.append('.');
            }
            path.append(pendingKey);
            pendingKey = null;
        }
        else if (event == JsonEvent.START_OBJECT)
        {
            if (pathIs("choices"))
            {
                choiceIndex = 0;
            }
            else if (toolCalls())
            {
                toolIndex = NO_INDEX;
                toolId = null;
                toolName = null;
                toolArguments = false;
            }
        }
    }

    private Status onContainerEnd(
        JsonEvent event)
    {
        if (event == JsonEvent.END_OBJECT && toolCalls() && !toolArguments && (toolId != null || toolName != null))
        {
            openTool();
            emit(0, !delta());
        }

        path.setLength(pathLengthAt[--depth]);

        return depth == 0 ? Status.COMPLETED : Status.ADVANCED;
    }

    private void onKeyName(
        JsonController control,
        JsonSource source)
    {
        if (source.deferredBytes())
        {
            control.consumed(0);
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
        if (pendingKey != null)
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
            else if (event == JsonEvent.VALUE_NUMBER)
            {
                onNumber(control, source, field, deferred);
            }

            if (!deferred)
            {
                pendingKey = null;
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
        case "choices.delta.content":
        case "choices.message.content":
            onText(control, source, TYPE_ASSISTANT_TEXT, deferred, field.startsWith("choices.message"));
            break;
        case "choices.delta.refusal":
        case "choices.message.refusal":
            onText(control, source, TYPE_ASSISTANT_REFUSAL, deferred, field.startsWith("choices.message"));
            break;
        case "choices.delta.tool_calls.id":
        case "choices.message.tool_calls.id":
            if (deferred)
            {
                control.consumed(0);
            }
            else
            {
                toolId = source.getString();
            }
            break;
        case "choices.delta.tool_calls.function.name":
        case "choices.message.tool_calls.function.name":
            if (deferred)
            {
                control.consumed(0);
            }
            else
            {
                toolName = source.getString();
            }
            break;
        case "choices.delta.tool_calls.function.arguments":
        case "choices.message.tool_calls.function.arguments":
            onArguments(control, source, deferred, field.startsWith("choices.message"));
            break;
        case "id":
        case "model":
        case "choices.finish_reason":
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

    private void onNumber(
        JsonController control,
        JsonSource source,
        String field,
        boolean deferred)
    {
        if (deferred)
        {
            control.consumed(0);
        }
        else
        {
            switch (field)
            {
            case "choices.index":
                choiceIndex = source.getInt();
                break;
            case "choices.delta.tool_calls.index":
            case "choices.message.tool_calls.index":
                toolIndex = source.getInt();
                break;
            default:
                break;
            }
        }
    }

    private void onText(
        JsonController control,
        JsonSource source,
        String type,
        boolean deferred,
        boolean atomic)
    {
        final CharSequence chars = source.getStringView();
        final int length = chars.length();

        if (length > 0 || highSurrogate != 0 || !deferred && atomic && sectionOpen)
        {
            if (!sectionOpen || !type.equals(sectionType) || sectionMessage != choiceIndex)
            {
                closeSection();
                openSection(type, choiceIndex, null, null, NO_INDEX);
            }

            emit(encode(chars, !deferred), !deferred && atomic);
        }

        control.consumed(length);
    }

    private void onArguments(
        JsonController control,
        JsonSource source,
        boolean deferred,
        boolean atomic)
    {
        final CharSequence chars = source.getStringView();
        final int length = chars.length();

        toolArguments = true;

        if (!sectionOpen || !continuesTool())
        {
            openTool();

            if (length == 0 && (deferred || !atomic))
            {
                emit(0, false);
            }
        }

        if (length > 0 || highSurrogate != 0 || !deferred && atomic && sectionOpen)
        {
            emit(encode(chars, !deferred), !deferred && atomic);
        }

        control.consumed(length);
    }

    private boolean continuesTool()
    {
        return TYPE_TOOL_CALL.equals(sectionType) &&
            sectionMessage == choiceIndex &&
            toolIndex != NO_INDEX &&
            sectionTool == toolIndex &&
            (toolId == null || toolId.equals(sectionToolId));
    }

    private void openTool()
    {
        if (!sectionOpen || !continuesTool())
        {
            closeSection();
            openSection(TYPE_TOOL_CALL, choiceIndex, toolId, toolName, toolIndex);
        }
    }

    private void openSection(
        String type,
        int message,
        String extension,
        String attributes,
        int tool)
    {
        sectionOpen = true;
        sectionStarted = false;
        sectionType = type;
        sectionMessage = message;
        sectionTool = tool;
        sectionToolId = extension;
        sectionExtension = extension;
        sectionAttributes = attributes;
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
                sectionMessage,
                sectionExtension,
                sectionAttributes,
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

    private boolean toolCalls()
    {
        final int length = path.length();

        return length > 0 && (path.indexOf("choices.delta.tool_calls") == 0 && length == "choices.delta.tool_calls".length() ||
            path.indexOf("choices.message.tool_calls") == 0 && length == "choices.message.tool_calls".length());
    }

    private boolean delta()
    {
        return path.indexOf("choices.delta") == 0;
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
            String type,
            int message,
            String extension,
            String attributes,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length);
    }
}
