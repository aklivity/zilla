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

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmErrorFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmUsageFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.engine.util.Flags;

final class LlmAnthropicResponseDecoder
{
    private static final int NONE = -1;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private static final String BLOCK_TOOL_USE = "tool_use";
    private static final String BLOCK_CONTENT = "content";
    private static final String BLOCK_CONTENT_BLOCK = "content_block";

    private static final String KEY_MESSAGE = "message";
    private static final String KEY_STOP_REASON = "stop_reason";
    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_MESSAGE_START = "message_start";
    private static final String TYPE_MESSAGE_DELTA = "message_delta";

    private final Output output;
    private final JsonParserEx parser;
    private final StringBuilder path;
    private final int[] pathLengthAt;
    private final int holdMax;

    private DirectBufferEx input;
    private int inputLimit;

    private boolean scanned;
    private boolean scanError;
    private boolean completed;
    private boolean rejected;

    private int depth;
    private String pendingKey;
    private String documentType;

    private int documentInputTokens = NONE;
    private int documentOutputTokens = NONE;
    private int documentCacheWriteTokens = NONE;
    private int documentCacheReadTokens = NONE;
    private String documentErrorType;
    private String documentErrorMessage;
    private String documentId;
    private String documentModel;
    private String documentFinishReason;

    private int inputTokens = NONE;
    private int outputTokens = NONE;
    private int cacheWriteTokens = NONE;
    private int cacheReadTokens = NONE;
    private String errorType;
    private String errorMessage;
    private String id;
    private String model;
    private String finishReason;

    private String blockType;
    private String toolId;
    private String toolName;

    private boolean sectionOpen;
    private boolean sectionStarted;
    private boolean sectionWritten;
    private boolean sectionAtomic;
    private LlmSectionType sectionType;
    private String sectionToolCallId;
    private String sectionToolName;

    private char highSurrogate;
    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;
    private byte[] heldBytes;
    private UnsafeBufferEx heldBuffer;
    private int heldLength;

    private boolean capturing;
    private boolean captureSection;
    private boolean captureStarted;
    private boolean captureTrim;
    private int captureDepth;
    private int captureFrom;
    private String captureName;
    private final StringBuilder captureValue;

    LlmAnthropicResponseDecoder(
        Output output,
        int holdMax)
    {
        this.output = output;
        this.captureValue = new StringBuilder();
        this.holdMax = holdMax;
        this.parser = JsonEx.createParser();
        this.path = new StringBuilder();
        this.pathLengthAt = new int[16];
        this.textBytes = new byte[1024];
        this.textBuffer = new UnsafeBufferEx(textBytes);
        this.heldBytes = new byte[1024];
        this.heldBuffer = new UnsafeBufferEx(heldBytes);
        this.heldLength = NONE;
    }

    boolean begun()
    {
        return scanned;
    }

    boolean failed()
    {
        return errorType != null || errorMessage != null;
    }

    boolean rejected()
    {
        return rejected;
    }

    boolean completed()
    {
        return completed;
    }

    String errorType()
    {
        return errorType;
    }

    String errorMessage()
    {
        return errorMessage;
    }

    String id()
    {
        return documentId != null ? documentId : id;
    }

    String model()
    {
        return documentModel != null ? documentModel : model;
    }

    String finishReason()
    {
        final String reported = documentFinishReason != null ? documentFinishReason : finishReason;

        String neutral = reported;

        if (reported != null)
        {
            neutral = switch (reported)
            {
            case "end_turn", "stop_sequence", "pause_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool-call";
            case "refusal" -> "content-filter";
            default -> reported;
            };
        }

        return neutral;
    }

    void populateUsage(
        LlmUsageFW.Builder usage)
    {
        usage.inputTokens(inputTokens)
            .cacheWriteTokens(cacheWriteTokens)
            .cacheReadTokens(cacheReadTokens)
            .outputTokens(outputTokens)
            .reasoningTokens(NONE)
            .totalTokens(NONE);

        final JsonObjectBuilder builder = Json.createObjectBuilder();
        addIfPresent(builder, "input_tokens", inputTokens);
        addIfPresent(builder, "cache_creation_input_tokens", cacheWriteTokens);
        addIfPresent(builder, "cache_read_input_tokens", cacheReadTokens);
        addIfPresent(builder, "output_tokens", outputTokens);

        final JsonObject nativeUsage = builder.build();
        if (!nativeUsage.isEmpty())
        {
            usage.nativeUsage(nativeUsage.toString());
        }
    }

    void populateError(
        LlmErrorFW.Builder error)
    {
        error.status(NONE)
            .type(errorType)
            .message(errorMessage);
    }

    void finish()
    {
        closeSection();
    }

    void nextDocument()
    {
        onDocumentEnd();
        parser.reset();
        completed = false;
        depth = 0;
        path.setLength(0);
        pendingKey = null;
        capturing = false;
    }

    void reset()
    {
        nextDocument();
        scanned = false;
        rejected = false;
        sectionOpen = false;
        sectionStarted = false;
        highSurrogate = 0;
        heldLength = NONE;
    }

    int decode(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        int progress = offset;

        try
        {
            if (completed)
            {
                progress = limit;

                for (int index = offset; index < limit; index++)
                {
                    if (!isWhitespace(buffer.getByte(index)))
                    {
                        rejected = true;
                    }
                }
            }
            else
            {
                boolean ready = scanned;

                if (!scanned)
                {
                    final boolean found = scan(buffer, offset, limit, last);

                    if (found || last || limit - offset >= holdMax)
                    {
                        scanned = true;
                        ready = true;

                        if (!scanError && !rejected)
                        {
                            output.begin();
                        }
                    }
                }

                if (rejected)
                {
                    progress = limit;
                }
                else if (ready)
                {
                    progress = decodeAvailable(buffer, offset, limit, last);
                }
            }
        }
        catch (JsonException ex)
        {
            rejected = true;
            progress = limit;
        }

        return progress;
    }

    private int decodeAvailable(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        int progress = offset;
        boolean advanced = true;

        while (advanced && !completed && !rejected)
        {
            int window = Math.min(limit - progress, Math.max(0, output.credit() - Math.max(heldLength, 0)));

            if (window == 0 && limit > progress && heldLength > 0 && output.credit() > 0)
            {
                flushHeld();
                window = Math.min(limit - progress, Math.max(0, output.credit() - Math.max(heldLength, 0)));
            }

            if (window > 0)
            {
                final int next = decodeWindow(buffer, progress, progress + window, last && progress + window == limit);

                advanced = next > progress && next < limit;
                progress = next;
            }
            else
            {
                if (limit == progress)
                {
                    progress = decodeWindow(buffer, progress, progress, last);
                }

                advanced = false;
            }
        }

        return progress;
    }

    private void flushHeld()
    {
        if (heldLength != NONE)
        {
            write(heldBuffer, 0, heldLength, false);
            heldLength = NONE;
        }
    }

    private boolean scan(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        parser.reset();
        parser.wrap(buffer, offset, limit, last);

        int scanDepth = 0;
        String scanKey = null;
        String scanParent = null;
        boolean found = false;

        scanError = false;

        while (!found && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                scanDepth++;
                if (scanDepth == 2)
                {
                    scanParent = scanKey;
                }
                break;
            case END_OBJECT:
            case END_ARRAY:
                scanDepth--;
                found = scanDepth == 0;
                break;
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    scanKey = parser.getString();
                    found = scanDepth == 1 && BLOCK_CONTENT.equals(scanKey);
                }
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                else if (scanKey != null)
                {
                    found = scanValue(scanDepth, scanKey, scanParent);
                }
                break;
            default:
                break;
            }
        }

        parser.reset();

        return found;
    }

    private boolean scanValue(
        int scanDepth,
        String scanKey,
        String scanParent)
    {
        boolean found = false;

        if (scanDepth == 1 && "type".equals(scanKey))
        {
            scanError = "error".equals(parser.getString());
            found = scanError;
        }
        else if (scanDepth == 1 && "id".equals(scanKey) ||
            scanDepth == 2 && KEY_MESSAGE.equals(scanParent) && "id".equals(scanKey))
        {
            documentId = parser.getString();
        }
        else if (scanDepth == 1 && "model".equals(scanKey) ||
            scanDepth == 2 && KEY_MESSAGE.equals(scanParent) && "model".equals(scanKey))
        {
            documentModel = parser.getString();
        }

        return found || documentId != null && documentModel != null;
    }

    private int decodeWindow(
        DirectBufferEx buffer,
        int offset,
        int windowLimit,
        boolean windowLast)
    {
        input = buffer;
        inputLimit = windowLimit;
        captureFrom = offset;

        parser.wrap(buffer, offset, windowLimit, windowLast);

        while (!completed && !rejected && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (capturing)
            {
                onCapture(event);
            }
            else
            {
                onEvent(event);
            }
        }

        if (capturing)
        {
            flushCapture(false);
        }

        int progress = position();

        if (completed)
        {
            while (progress < windowLimit && isWhitespace(buffer.getByte(progress)))
            {
                progress++;
            }
        }

        return rejected ? windowLimit : progress;
    }

    private int position()
    {
        return inputLimit - parser.remaining();
    }

    private void onEvent(
        JsonEvent event)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            onContainerStart(event);
            break;
        case END_OBJECT:
        case END_ARRAY:
            onContainerEnd(event);
            break;
        case KEY_NAME:
            onKeyName();
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            onLeafValue(event);
            break;
        default:
            break;
        }
    }

    private void onContainerStart(
        JsonEvent event)
    {
        if (depth == 0 && event != JsonEvent.START_OBJECT)
        {
            rejected = true;
        }

        pathLengthAt[depth++] = path.length();

        if (pendingKey != null)
        {
            if (path.length() > 0)
            {
                path.append('.');
            }
            path.append(pendingKey);

            if ("input".equals(pendingKey) && event == JsonEvent.START_OBJECT && pathIs("content.input"))
            {
                beginSectionCapture();
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

    private void onContainerEnd(
        JsonEvent event)
    {
        if (event == JsonEvent.END_OBJECT && pathIs(BLOCK_CONTENT_BLOCK) && BLOCK_TOOL_USE.equals(blockType))
        {
            closeSection();
            openSection(LlmSectionType.TOOL_CALL, toolId, toolName);
            emit(0, false);
        }

        path.setLength(pathLengthAt[--depth]);

        if (depth == 0)
        {
            completed = true;
            onDocumentEnd();
        }
    }

    private void onKeyName()
    {
        if (parser.deferredBytes())
        {
            parser.consumed(0);
        }
        else
        {
            pendingKey = parser.getString();

            if (memberScope() && !canonical(pendingKey))
            {
                beginMemberCapture(pendingKey);
            }
        }
    }

    private boolean memberScope()
    {
        return documentType != null && switch (documentType)
        {
        case TYPE_MESSAGE -> depth == 1;
        case TYPE_MESSAGE_START -> depth == 2 && pathIs(KEY_MESSAGE);
        case TYPE_MESSAGE_DELTA -> depth == 2 && pathIs("delta");
        default -> false;
        };
    }

    private boolean canonical(
        String member)
    {
        return switch (documentType)
        {
        case TYPE_MESSAGE -> canonicalMessage(member) || "error".equals(member);
        case TYPE_MESSAGE_START -> canonicalMessage(member);
        default -> false;
        };
    }

    private static boolean canonicalMessage(
        String member)
    {
        return switch (member)
        {
        case "id", "type", "role", "model", "content", "usage" -> true;
        default -> false;
        };
    }

    private void onLeafValue(
        JsonEvent event)
    {
        if (pendingKey != null)
        {
            final boolean deferred = parser.deferredBytes();
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
                onString(field, deferred);
            }
            else if (event == JsonEvent.VALUE_NUMBER)
            {
                onNumber(field, deferred);
            }

            if (!deferred)
            {
                pendingKey = null;
            }
        }
    }

    private void onString(
        String field,
        boolean deferred)
    {
        switch (field)
        {
        case "type":
            onEventType(deferred);
            break;
        case "content_block.type":
        case "content.type":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                blockType = parser.getString();
            }
            break;
        case "content_block.id":
        case "content.id":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                toolId = parser.getString();
            }
            break;
        case "content_block.name":
        case "content.name":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                toolName = parser.getString();
            }
            break;
        case "content.text":
            onText(LlmSectionType.ASSISTANT_TEXT, deferred, true);
            break;
        case "content.thinking":
            onText(LlmSectionType.ASSISTANT_REASONING, deferred, true);
            break;
        case "delta.text":
            onText(LlmSectionType.ASSISTANT_TEXT, deferred, false);
            break;
        case "delta.thinking":
            onText(LlmSectionType.ASSISTANT_REASONING, deferred, false);
            break;
        case "delta.partial_json":
            onArguments(deferred);
            break;
        case "error.type":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                documentErrorType = parser.getString();
            }
            break;
        case "error.message":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                documentErrorMessage = parser.getString();
            }
            break;
        case "id":
        case "message.id":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                documentId = parser.getString();
            }
            break;
        case "model":
        case "message.model":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                documentModel = parser.getString();
            }
            break;
        default:
            parser.consumed(parser.getStringView().length());
            break;
        }
    }

    private void onNumber(
        String field,
        boolean deferred)
    {
        if (deferred)
        {
            parser.consumed(0);
        }
        else
        {
            switch (field)
            {
            case "message.usage.input_tokens":
            case "usage.input_tokens":
                documentInputTokens = parser.getInt();
                break;
            case "message.usage.cache_creation_input_tokens":
            case "usage.cache_creation_input_tokens":
                documentCacheWriteTokens = parser.getInt();
                break;
            case "message.usage.cache_read_input_tokens":
            case "usage.cache_read_input_tokens":
                documentCacheReadTokens = parser.getInt();
                break;
            case "usage.output_tokens":
                documentOutputTokens = parser.getInt();
                break;
            default:
                break;
            }
        }
    }

    private void onEventType(
        boolean deferred)
    {
        final CharSequence type = parser.getStringView();

        if (deferred)
        {
            parser.consumed(0);
        }
        else
        {
            documentType = type.toString();

            if (matches(type, "content_block_stop") || matches(type, TYPE_MESSAGE_DELTA) || matches(type, "message_stop"))
            {
                closeSection();
            }
        }
    }

    private void onText(
        LlmSectionType type,
        boolean deferred,
        boolean atomic)
    {
        final CharSequence chars = parser.getStringView();
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

        if (deferred)
        {
            parser.consumed(length);
        }
    }

    private void onArguments(
        boolean deferred)
    {
        final CharSequence chars = parser.getStringView();
        final int length = chars.length();

        if (sectionOpen && sectionType == LlmSectionType.TOOL_CALL && (length > 0 || highSurrogate != 0))
        {
            emit(encode(chars, !deferred), false);
        }

        if (deferred)
        {
            parser.consumed(length);
        }
    }

    private void openSection(
        LlmSectionType type,
        String toolCallId,
        String toolName)
    {
        sectionOpen = true;
        sectionStarted = false;
        sectionWritten = false;
        sectionAtomic = pathIs(BLOCK_CONTENT) || path.indexOf(BLOCK_CONTENT + ".") == 0;
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
            sectionStarted = true;
            sectionOpen = !fin;

            if (sectionAtomic)
            {
                emitAtomic(length, fin);
            }
            else
            {
                write(textBuffer, 0, length, fin);
            }
        }
    }

    private void emitAtomic(
        int length,
        boolean fin)
    {
        if (heldLength != NONE && fin && length == 0)
        {
            write(heldBuffer, 0, heldLength, true);
            heldLength = NONE;
        }
        else
        {
            if (heldLength != NONE)
            {
                write(heldBuffer, 0, heldLength, false);
                heldLength = NONE;
            }

            if (fin)
            {
                write(textBuffer, 0, length, true);
            }
            else
            {
                if (heldBytes.length < length)
                {
                    heldBytes = new byte[length];
                    heldBuffer = new UnsafeBufferEx(heldBytes);
                }

                textBuffer.getBytes(0, heldBytes, 0, length);
                heldLength = length;
            }
        }
    }

    private void write(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean fin)
    {
        final int flags = sectionWritten ? Flags.NONE : Flags.INIT;

        sectionWritten = true;

        output.frame(
            sectionType,
            0,
            sectionToolCallId,
            sectionToolName,
            fin ? Flags.fin(flags) : flags,
            buffer,
            offset,
            length);
    }

    private void beginMemberCapture(
        String name)
    {
        capturing = true;
        captureSection = false;
        captureName = name;
        captureValue.setLength(0);
        captureStarted = false;
        captureTrim = true;
        captureDepth = 0;
        captureFrom = position();
        pendingKey = null;

        closeSection();
    }

    private void beginSectionCapture()
    {
        capturing = true;
        captureSection = true;
        captureStarted = false;
        captureTrim = true;
        captureDepth = 1;
        captureFrom = position() - 1;

        if (BLOCK_TOOL_USE.equals(blockType))
        {
            closeSection();
            openSection(LlmSectionType.TOOL_CALL, toolId, toolName);
            sectionAtomic = false;
            sectionStarted = true;
        }
        else
        {
            capturing = false;
        }
    }

    private void onCapture(
        JsonEvent event)
    {
        final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            captureDepth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            captureDepth--;
            break;
        case VALUE_STRING:
            if (deferred)
            {
                parser.consumed(parser.getStringView().length());
            }
            break;
        default:
            break;
        }

        if (captureDepth == 0 && !deferred)
        {
            flushCapture(true);
            capturing = false;

            if (captureSection)
            {
                sectionOpen = false;
                path.setLength(pathLengthAt[--depth]);
            }
        }
    }

    private void flushCapture(
        boolean fin)
    {
        final int end = position();
        int from = captureFrom;

        if (captureTrim)
        {
            while (from < end && isSeparator(input.getByte(from)))
            {
                from++;
            }

            captureTrim = from == end;
        }

        if (end > from || fin)
        {
            if (captureSection)
            {
                final int flags = captureStarted
                    ? fin ? Flags.FIN : Flags.NONE
                    : fin ? Flags.COMPLETE : Flags.INIT;

                captureStarted = !fin;
                sectionWritten = captureStarted || fin;

                output.frame(sectionType, 0, sectionToolCallId, sectionToolName, flags, input, from, end - from);
            }
            else
            {
                final int flags = captureStarted
                    ? fin ? Flags.FIN : Flags.NONE
                    : fin ? Flags.COMPLETE : Flags.INIT;

                captureStarted = !fin;

                if (KEY_STOP_REASON.equals(captureName))
                {
                    captureStopReason(from, end, fin);
                }

                output.block(captureName, flags, input, from, end - from);
            }
        }

        captureFrom = end;
    }

    private void captureStopReason(
        int from,
        int end,
        boolean fin)
    {
        for (int index = from; index < end; index++)
        {
            captureValue.append((char) input.getByte(index));
        }

        if (fin)
        {
            final String value = captureValue.toString().strip();

            if (value.length() > 1 && value.charAt(0) == '"')
            {
                documentFinishReason = value.substring(1, value.length() - 1);
            }
        }
    }

    private void onDocumentEnd()
    {
        inputTokens = documentInputTokens != NONE ? documentInputTokens : inputTokens;
        outputTokens = documentOutputTokens != NONE ? documentOutputTokens : outputTokens;
        cacheWriteTokens = documentCacheWriteTokens != NONE ? documentCacheWriteTokens : cacheWriteTokens;
        cacheReadTokens = documentCacheReadTokens != NONE ? documentCacheReadTokens : cacheReadTokens;
        errorType = documentErrorType != null ? documentErrorType : errorType;
        errorMessage = documentErrorMessage != null ? documentErrorMessage : errorMessage;
        id = documentId != null ? documentId : id;
        model = documentModel != null ? documentModel : model;
        finishReason = documentFinishReason != null ? documentFinishReason : finishReason;

        documentInputTokens = NONE;
        documentOutputTokens = NONE;
        documentCacheWriteTokens = NONE;
        documentCacheReadTokens = NONE;
        documentErrorType = null;
        documentErrorMessage = null;
        documentId = null;
        documentModel = null;
        documentFinishReason = null;
        documentType = null;
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

    private static boolean isSeparator(
        byte value)
    {
        return value == ':' || isWhitespace(value);
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static void addIfPresent(
        JsonObjectBuilder builder,
        String name,
        int value)
    {
        if (value != NONE)
        {
            builder.add(name, value);
        }
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
        int credit();

        void begin();

        void frame(
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            int flags,
            DirectBufferEx buffer,
            int offset,
            int length);

        void block(
            String block,
            int flags,
            DirectBufferEx buffer,
            int offset,
            int length);
    }
}
