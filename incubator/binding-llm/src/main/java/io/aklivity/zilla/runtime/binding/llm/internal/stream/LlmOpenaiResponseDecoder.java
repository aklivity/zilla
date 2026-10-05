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

final class LlmOpenaiResponseDecoder
{
    private static final int NONE = -1;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final String LOGPROBS = "logprobs";
    private static final String FINISH_REASON = "finish_reason";

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

    private int documentInputTokens = NONE;
    private int documentOutputTokens = NONE;
    private int documentTotalTokens = NONE;
    private int documentCacheReadTokens = NONE;
    private int documentReasoningTokens = NONE;
    private String documentErrorType;
    private String documentErrorMessage;
    private String documentId;
    private String documentModel;
    private String documentFinishReason;

    private int inputTokens = NONE;
    private int outputTokens = NONE;
    private int totalTokens = NONE;
    private int cacheReadTokens = NONE;
    private int reasoningTokens = NONE;
    private String errorType;
    private String errorMessage;
    private String id;
    private String model;
    private String finishReason;

    private int choiceIndex;
    private int toolIndex;
    private String toolId;
    private String toolName;
    private boolean toolArguments;

    private boolean sectionOpen;
    private boolean sectionStarted;
    private boolean sectionWritten;
    private boolean sectionAtomic;
    private boolean sectionFollow;
    private boolean followPending;
    private boolean logprobs;
    private LlmSectionType sectionType;
    private int sectionMessage;
    private int sectionTool;
    private String sectionToolId;
    private String sectionToolName;

    private char highSurrogate;
    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;
    private byte[] heldBytes;
    private UnsafeBufferEx heldBuffer;
    private int heldLength;

    private boolean capturing;
    private boolean captureAfter;
    private boolean captureStarted;
    private boolean captureTrim;
    private int captureDepth;
    private int captureFrom;
    private String captureName;
    private boolean choiceMessage;
    private final StringBuilder captureValue;

    LlmOpenaiResponseDecoder(
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
        this.sectionTool = NONE;
    }

    void logprobs(
        boolean requested)
    {
        this.logprobs = requested;
    }

    boolean failed()
    {
        return errorType != null || errorMessage != null;
    }

    boolean begun()
    {
        return scanned;
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
            case "tool_calls", "function_call" -> "tool-call";
            case "content_filter" -> "content-filter";
            default -> reported;
            };
        }

        return neutral;
    }

    void populateUsage(
        LlmUsageFW.Builder usage)
    {
        usage.inputTokens(inputTokens)
            .cacheWriteTokens(NONE)
            .cacheReadTokens(cacheReadTokens)
            .outputTokens(outputTokens)
            .reasoningTokens(reasoningTokens)
            .totalTokens(totalTokens);

        final JsonObjectBuilder builder = Json.createObjectBuilder();
        addIfPresent(builder, "input_tokens", inputTokens);
        addIfPresent(builder, "cache_read_tokens", cacheReadTokens);
        addIfPresent(builder, "output_tokens", outputTokens);
        addIfPresent(builder, "reasoning_tokens", reasoningTokens);
        addIfPresent(builder, "total_tokens", totalTokens);

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
        sectionFollow = false;
        followPending = false;
        sectionTool = NONE;
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
            write(heldBuffer, heldLength, false);
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
        boolean scanChoices = false;
        boolean found = false;

        scanError = false;

        while (!found && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (scanChoices)
            {
                rejected = event != JsonEvent.START_ARRAY;
                found = true;
            }
            else
            {
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    scanDepth++;
                    scanKey = null;
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
                    else if (scanDepth == 1)
                    {
                        scanKey = parser.getString();
                        scanError = "error".equals(scanKey);
                        scanChoices = "choices".equals(scanKey);
                        found = scanError;
                    }
                    break;
                case VALUE_STRING:
                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
                    }
                    else if (scanDepth == 1 && scanKey != null)
                    {
                        switch (scanKey)
                        {
                        case "id":
                            documentId = parser.getString();
                            break;
                        case "model":
                            documentModel = parser.getString();
                            break;
                        default:
                            break;
                        }
                        found = documentId != null && documentModel != null;
                    }
                    break;
                default:
                    break;
                }
            }
        }

        parser.reset();

        return found;
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
                toolIndex = NONE;
                toolId = null;
                toolName = null;
                toolArguments = false;
            }
        }
    }

    private void onContainerEnd(
        JsonEvent event)
    {
        if (event == JsonEvent.END_OBJECT && toolCalls() && !toolArguments && (toolId != null || toolName != null))
        {
            openTool();
            emit(0, !delta());
        }
        else if (event == JsonEvent.END_OBJECT && followPending && pathIs("choices"))
        {
            emitAfterEmpty();
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

            if (depth == 1 && !canonical(pendingKey))
            {
                beginCapture(pendingKey, false);
            }
            else if (depth == 3 && pathIs("choices"))
            {
                onChoiceKey();
            }
        }
    }

    private void onChoiceKey()
    {
        if (LOGPROBS.equals(pendingKey))
        {
            beginCapture(LOGPROBS, true);
        }
        else if ("message".equals(pendingKey))
        {
            choiceMessage = true;
        }
        else if (choiceMessage && FINISH_REASON.equals(pendingKey))
        {
            beginCapture(FINISH_REASON, true);
        }
    }

    private static boolean canonical(
        String member)
    {
        return switch (member)
        {
        case "id", "object", "model", "choices", "usage", "error" -> true;
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

            if ("choices".equals(field))
            {
                rejected = true;
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
        case "choices.delta.content":
        case "choices.message.content":
            onText(LlmSectionType.ASSISTANT_TEXT, deferred, field.startsWith("choices.message"));
            break;
        case "choices.delta.refusal":
        case "choices.message.refusal":
            onText(LlmSectionType.ASSISTANT_REFUSAL, deferred, field.startsWith("choices.message"));
            break;
        case "choices.delta.tool_calls.id":
        case "choices.message.tool_calls.id":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                toolId = parser.getString();
            }
            break;
        case "choices.delta.tool_calls.function.name":
        case "choices.message.tool_calls.function.name":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                toolName = parser.getString();
            }
            break;
        case "choices.delta.tool_calls.function.arguments":
        case "choices.message.tool_calls.function.arguments":
            onArguments(deferred, field.startsWith("choices.message"));
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
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                documentModel = parser.getString();
            }
            break;
        case "choices.finish_reason":
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                documentFinishReason = parser.getString();
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
            case "choices.index":
                choiceIndex = parser.getInt();
                break;
            case "choices.delta.tool_calls.index":
            case "choices.message.tool_calls.index":
                toolIndex = parser.getInt();
                break;
            case "usage.prompt_tokens":
                documentInputTokens = parser.getInt();
                break;
            case "usage.completion_tokens":
                documentOutputTokens = parser.getInt();
                break;
            case "usage.total_tokens":
                documentTotalTokens = parser.getInt();
                break;
            case "usage.prompt_tokens_details.cached_tokens":
                documentCacheReadTokens = parser.getInt();
                break;
            case "usage.completion_tokens_details.reasoning_tokens":
                documentReasoningTokens = parser.getInt();
                break;
            default:
                break;
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
            if (!sectionOpen || type != sectionType || sectionMessage != choiceIndex)
            {
                closeSection();
                openSection(type, choiceIndex, null, null, NONE);
                sectionFollow = logprobs && !atomic;
            }

            emit(encode(chars, !deferred), !deferred && (atomic || sectionFollow));
        }

        if (deferred)
        {
            parser.consumed(length);
        }
    }

    private void onArguments(
        boolean deferred,
        boolean atomic)
    {
        final CharSequence chars = parser.getStringView();
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

        if (deferred)
        {
            parser.consumed(length);
        }
    }

    private boolean continuesTool()
    {
        return sectionType == LlmSectionType.TOOL_CALL &&
            sectionMessage == choiceIndex &&
            toolIndex != NONE &&
            sectionTool == toolIndex &&
            (toolId == null || toolId.equals(sectionToolId));
    }

    private void openTool()
    {
        if (!sectionOpen || !continuesTool())
        {
            closeSection();
            openSection(LlmSectionType.TOOL_CALL, choiceIndex, toolId, toolName, toolIndex);
        }
    }

    private void openSection(
        LlmSectionType type,
        int message,
        String toolCallId,
        String toolName,
        int tool)
    {
        sectionOpen = true;
        sectionStarted = false;
        sectionWritten = false;
        sectionAtomic = !delta();
        sectionFollow = false;
        sectionType = type;
        sectionMessage = message;
        sectionTool = tool;
        sectionToolId = toolCallId;
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
                write(textBuffer, length, fin);
            }
        }
    }

    private void emitAtomic(
        int length,
        boolean fin)
    {
        if (heldLength != NONE && fin && length == 0)
        {
            write(heldBuffer, heldLength, true);
            heldLength = NONE;
        }
        else
        {
            if (heldLength != NONE)
            {
                write(heldBuffer, heldLength, false);
                heldLength = NONE;
            }

            if (fin)
            {
                write(textBuffer, length, true);
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
        int length,
        boolean fin)
    {
        final int flags = sectionWritten ? Flags.NONE : Flags.INIT;

        sectionWritten = true;

        output.frame(
            sectionType,
            sectionMessage,
            sectionToolId,
            sectionToolName,
            sectionFollow,
            fin ? Flags.fin(flags) : flags,
            buffer,
            0,
            length);

        followPending = fin && sectionFollow;
    }

    private void emitAfterEmpty()
    {
        followPending = false;
        output.after(LOGPROBS, Flags.COMPLETE, textBuffer, 0, 0);
    }

    private void beginCapture(
        String name,
        boolean after)
    {
        capturing = true;
        captureAfter = after;
        captureName = name;
        captureValue.setLength(0);
        captureStarted = false;
        captureTrim = true;
        captureDepth = 0;
        captureFrom = position();
        pendingKey = null;

        closeSection();
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
            if (captureAfter && !captureStarted && event == JsonEvent.VALUE_NULL)
            {
                capturing = false;

                if (followPending)
                {
                    emitAfterEmpty();
                }
            }
            else
            {
                flushCapture(true);
                capturing = false;
                followPending = followPending && !captureAfter;
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
            final int flags = captureStarted
                ? fin ? Flags.FIN : Flags.NONE
                : fin ? Flags.COMPLETE : Flags.INIT;

            captureStarted = !fin;

            if (captureAfter)
            {
                if (FINISH_REASON.equals(captureName))
                {
                    captureFinishReason(from, end, fin);
                }

                output.after(captureName, flags, input, from, end - from);
            }
            else
            {
                output.block(captureName, flags, input, from, end - from);
            }
        }

        captureFrom = end;
    }

    private void captureFinishReason(
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
        totalTokens = documentTotalTokens != NONE ? documentTotalTokens : totalTokens;
        cacheReadTokens = documentCacheReadTokens != NONE ? documentCacheReadTokens : cacheReadTokens;
        reasoningTokens = documentReasoningTokens != NONE ? documentReasoningTokens : reasoningTokens;
        errorType = documentErrorType != null ? documentErrorType : errorType;
        errorMessage = documentErrorMessage != null ? documentErrorMessage : errorMessage;
        id = documentId != null ? documentId : id;
        model = documentModel != null ? documentModel : model;
        finishReason = documentFinishReason != null ? documentFinishReason : finishReason;

        documentInputTokens = NONE;
        documentOutputTokens = NONE;
        documentTotalTokens = NONE;
        documentCacheReadTokens = NONE;
        documentReasoningTokens = NONE;
        documentErrorType = null;
        documentErrorMessage = null;
        documentId = null;
        documentModel = null;
        documentFinishReason = null;
        choiceMessage = false;
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
            boolean follow,
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

        void after(
            String block,
            int flags,
            DirectBufferEx buffer,
            int offset,
            int length);
    }
}
