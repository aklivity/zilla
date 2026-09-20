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
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Decodes Anthropic's native event sequence into the canonical vocabulary, driven by real
 * {@link JsonEvent}s from a long-lived {@link io.aklivity.zilla.runtime.common.json.JsonPipeline}
 * (see {@link LlmCanonicalEmitter}). A streaming chunk's dispatch is keyed by its out-of-band SSE
 * {@code event:} name, set via {@link #event(String)} before the chunk reaches the pipeline; a
 * non-streaming whole document has no such framing, so {@code event(null)} signals
 * {@link #onDocumentEnd()} to walk that document's own array-of-blocks shape via {@link #onWholeMessage()}.
 */
final class LlmAnthropicDecodeTransform extends LlmCanonicalEmitter implements LlmDialectEvent
{
    private static final String MESSAGE_START = "message_start";
    private static final String CONTENT_BLOCK_START = "content_block_start";
    private static final String CONTENT_BLOCK_DELTA = "content_block_delta";
    private static final String CONTENT_BLOCK_STOP = "content_block_stop";
    private static final String MESSAGE_DELTA = "message_delta";
    private static final String MESSAGE_STOP = "message_stop";

    private static final String TOOL_USE = "tool_use";
    private static final int ARGS_BUFFER_CAPACITY = 8192;

    private int inputTokens = -1;
    private LlmCanonicalBlockKind openBlockType;
    private int openBlockId;
    private int nextBlockId;

    private String nativeEvent;

    private final StringBuilder path;
    private final int[] pathLengthAt;
    private int depth;
    private String pendingKey;

    private boolean inContentElement;
    private int contentElementDepth;
    private final List<ContentBlock> contentBlocks;

    private boolean capturingArgs;
    private int captureDepth;
    private String argsPendingKey;
    private final JsonGeneratorEx argsGenerator;
    private final MutableDirectBufferEx argsBuffer;

    private String fieldId;
    private String fieldModel;
    private String fieldRole;
    private int fieldInputTokens = -1;
    private int fieldBlockId;
    private String fieldBlockType;
    private String fieldToolId;
    private String fieldToolName;
    private String fieldContent;
    private String fieldToolCallArguments;
    private String fieldFinishReason;
    private boolean fieldFinishReasonPresent;
    private int fieldOutputTokens = -1;

    LlmAnthropicDecodeTransform()
    {
        this.path = new StringBuilder();
        this.pathLengthAt = new int[16];
        this.contentBlocks = new ArrayList<>();
        this.argsGenerator = JsonEx.createGenerator();
        this.argsBuffer = new UnsafeBufferEx(new byte[ARGS_BUFFER_CAPACITY]);
        this.argsGenerator.wrap(argsBuffer, 0, argsBuffer.capacity());
    }

    @Override
    public void event(
        String name)
    {
        this.nativeEvent = name;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        if (event == JsonEvent.END_DOCUMENT)
        {
            onDocumentEnd();
            status = fireQueued(sink);
            clearFields();
        }
        else
        {
            onEvent(control, source, event);
            status = Status.ADVANCED;
        }
        return status;
    }

    private void onEvent(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        if (capturingArgs)
        {
            onCaptureEvent(control, source, event);
            return;
        }

        switch (event)
        {
        case KEY_NAME:
            if (source.deferredBytes())
            {
                control.consumed(0);
            }
            else
            {
                pendingKey = source.getString();
            }
            break;
        case START_OBJECT:
        case START_ARRAY:
            onContainerStart(event);
            break;
        case END_OBJECT:
        case END_ARRAY:
            onContainerEnd();
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
    }

    private void onContainerStart(
        JsonEvent event)
    {
        if (event == JsonEvent.START_OBJECT && "input".equals(pendingKey) && inContentElement)
        {
            capturingArgs = true;
            captureDepth = 1;
            argsGenerator.reset();
            argsGenerator.wrap(argsBuffer, 0, argsBuffer.capacity());
            argsGenerator.writeStartObject();
            pendingKey = null;
            return;
        }

        boolean enteringContentElement = event == JsonEvent.START_OBJECT && pendingKey == null &&
            !inContentElement && "content".contentEquals(path);

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

        if (enteringContentElement)
        {
            inContentElement = true;
            contentElementDepth = depth;
        }
    }

    private void onContainerEnd()
    {
        if (inContentElement && depth == contentElementDepth)
        {
            onContentElementEnd();
            inContentElement = false;
        }
        path.setLength(pathLengthAt[--depth]);
    }

    private void onCaptureEvent(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        switch (event)
        {
        case START_OBJECT:
            captureDepth++;
            writeArgsContainerStart(true);
            break;
        case START_ARRAY:
            captureDepth++;
            writeArgsContainerStart(false);
            break;
        case END_OBJECT:
        case END_ARRAY:
            argsGenerator.writeEnd();
            captureDepth--;
            if (captureDepth == 0)
            {
                endArgsCapture();
            }
            break;
        case KEY_NAME:
            if (source.deferredBytes())
            {
                control.consumed(0);
            }
            else
            {
                argsPendingKey = source.getString();
            }
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            onArgsLeafValue(source, event);
            break;
        default:
            break;
        }
    }

    private void writeArgsContainerStart(
        boolean object)
    {
        if (argsPendingKey != null)
        {
            if (object)
            {
                argsGenerator.writeStartObject(argsPendingKey);
            }
            else
            {
                argsGenerator.writeStartArray(argsPendingKey);
            }
            argsPendingKey = null;
        }
        else if (object)
        {
            argsGenerator.writeStartObject();
        }
        else
        {
            argsGenerator.writeStartArray();
        }
    }

    private void onArgsLeafValue(
        JsonSource source,
        JsonEvent event)
    {
        switch (event)
        {
        case VALUE_STRING:
            writeArgsValue(source.getString());
            break;
        case VALUE_NUMBER:
            writeArgsNumber(source.getBigDecimal());
            break;
        case VALUE_TRUE:
            writeArgsBoolean(true);
            break;
        case VALUE_FALSE:
            writeArgsBoolean(false);
            break;
        case VALUE_NULL:
            writeArgsNull();
            break;
        default:
            break;
        }
    }

    private void writeArgsValue(
        String value)
    {
        if (argsPendingKey != null)
        {
            argsGenerator.write(argsPendingKey, value);
            argsPendingKey = null;
        }
        else
        {
            argsGenerator.write(value);
        }
    }

    private void writeArgsNumber(
        BigDecimal value)
    {
        if (argsPendingKey != null)
        {
            argsGenerator.write(argsPendingKey, value);
            argsPendingKey = null;
        }
        else
        {
            argsGenerator.write(value);
        }
    }

    private void writeArgsBoolean(
        boolean value)
    {
        if (argsPendingKey != null)
        {
            argsGenerator.write(argsPendingKey, value);
            argsPendingKey = null;
        }
        else
        {
            argsGenerator.write(value);
        }
    }

    private void writeArgsNull()
    {
        if (argsPendingKey != null)
        {
            argsGenerator.writeNull(argsPendingKey);
            argsPendingKey = null;
        }
        else
        {
            argsGenerator.writeNull();
        }
    }

    private void endArgsCapture()
    {
        fieldToolCallArguments = argsBuffer.getStringWithoutLengthUtf8(0, argsGenerator.length());
        argsGenerator.reset();
        argsGenerator.wrap(argsBuffer, 0, argsBuffer.capacity());
        capturingArgs = false;
    }

    private void onLeafValue(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        if (pendingKey == null)
        {
            return;
        }

        if (source.deferredBytes())
        {
            control.consumed(0);
            return;
        }

        int fieldAt = path.length();
        if (fieldAt > 0)
        {
            path.append('.');
        }
        path.append(pendingKey);
        onField(path.toString(), source, event);
        path.setLength(fieldAt);
        pendingKey = null;
    }

    private void onField(
        String fieldPath,
        JsonSource source,
        JsonEvent event)
    {
        switch (fieldPath)
        {
        case "message.id":
            fieldId = source.getString();
            break;
        case "message.model":
            fieldModel = source.getString();
            break;
        case "message.role":
            fieldRole = source.getString();
            break;
        case "message.usage.input_tokens":
            fieldInputTokens = source.getInt();
            break;
        case "index":
            fieldBlockId = source.getInt();
            break;
        case "content_block.type":
            fieldBlockType = source.getString();
            break;
        case "content_block.id":
            fieldToolId = source.getString();
            break;
        case "content_block.name":
            fieldToolName = source.getString();
            break;
        case "delta.type":
            fieldBlockType = source.getString();
            break;
        case "delta.text":
            fieldContent = source.getString();
            break;
        case "delta.partial_json":
            fieldToolCallArguments = source.getString();
            break;
        case "delta.stop_reason":
            fieldFinishReasonPresent = true;
            fieldFinishReason = event == JsonEvent.VALUE_STRING ? source.getString() : null;
            break;
        case "usage.output_tokens":
            fieldOutputTokens = source.getInt();
            break;
        case "id":
            fieldId = source.getString();
            break;
        case "role":
            fieldRole = source.getString();
            break;
        case "model":
            fieldModel = source.getString();
            break;
        case "stop_reason":
            fieldFinishReasonPresent = true;
            fieldFinishReason = event == JsonEvent.VALUE_STRING ? source.getString() : null;
            break;
        case "usage.input_tokens":
            fieldInputTokens = source.getInt();
            break;
        case "content.type":
            fieldBlockType = source.getString();
            break;
        case "content.text":
            fieldContent = source.getString();
            break;
        case "content.id":
            fieldToolId = source.getString();
            break;
        case "content.name":
            fieldToolName = source.getString();
            break;
        default:
            break;
        }
    }

    private void onContentElementEnd()
    {
        ContentBlock block = new ContentBlock();
        block.kind = TOOL_USE.equals(fieldBlockType) ? LlmCanonicalBlockKind.TOOL_CALL : LlmCanonicalBlockKind.TEXT;
        block.toolId = fieldToolId;
        block.toolName = fieldToolName;
        block.text = block.kind == LlmCanonicalBlockKind.TOOL_CALL ? fieldToolCallArguments : fieldContent;
        contentBlocks.add(block);

        fieldBlockType = null;
        fieldToolId = null;
        fieldToolName = null;
        fieldContent = null;
        fieldToolCallArguments = null;
    }

    private void onDocumentEnd()
    {
        if (nativeEvent == null)
        {
            onWholeMessage();
            return;
        }

        switch (nativeEvent)
        {
        case MESSAGE_START:
            onMessageStart();
            break;
        case CONTENT_BLOCK_START:
            onContentBlockStart();
            break;
        case CONTENT_BLOCK_DELTA:
            onContentBlockDelta();
            break;
        case CONTENT_BLOCK_STOP:
            onContentBlockStop();
            break;
        case MESSAGE_DELTA:
            onMessageDelta();
            break;
        case MESSAGE_STOP:
            queueEnd();
            break;
        default:
            break;
        }
    }

    private void onMessageStart()
    {
        inputTokens = fieldInputTokens;
        queueMessageStart(0, fieldId, fieldModel, fieldRole);
    }

    private void onContentBlockStart()
    {
        boolean toolCall = TOOL_USE.equals(fieldBlockType);
        openBlockType = toolCall ? LlmCanonicalBlockKind.TOOL_CALL : LlmCanonicalBlockKind.TEXT;

        if (toolCall)
        {
            queueBlockStart(0, fieldBlockId, LlmCanonicalBlockKind.TOOL_CALL, fieldToolId, fieldToolName);
        }
    }

    private void onContentBlockDelta()
    {
        boolean toolCall = "input_json_delta".equals(fieldBlockType);
        String content = toolCall ? orDefault(fieldToolCallArguments, "") : orDefault(fieldContent, "");
        queueData(content);
    }

    private void onContentBlockStop()
    {
        if (openBlockType == LlmCanonicalBlockKind.TOOL_CALL)
        {
            queueBlockEnd(0, fieldBlockId);
        }
        openBlockType = null;
    }

    private void onMessageDelta()
    {
        queueFinish(0, finishReason(fieldFinishReasonPresent ? fieldFinishReason : null));
        queueUsage(inputTokens, fieldOutputTokens);
    }

    private void onWholeMessage()
    {
        queueMessageStart(0, fieldId, fieldModel, fieldRole);

        for (int i = 0; i < contentBlocks.size(); i++)
        {
            ContentBlock block = contentBlocks.get(i);
            int blockId = nextBlockId++;
            queueBlockStart(0, blockId, block.kind, block.toolId, block.toolName);
            String text = orDefault(block.text, "");
            if (!text.isEmpty())
            {
                queueData(text);
            }
            queueBlockEnd(0, blockId);
        }
        contentBlocks.clear();

        queueFinish(0, finishReason(fieldFinishReasonPresent ? fieldFinishReason : null));
        queueUsage(fieldInputTokens, fieldOutputTokens);
        queueEnd();
    }

    private void clearFields()
    {
        nativeEvent = null;
        path.setLength(0);
        depth = 0;
        pendingKey = null;
        inContentElement = false;
        contentElementDepth = 0;
        contentBlocks.clear();

        capturingArgs = false;
        captureDepth = 0;
        argsPendingKey = null;
        argsGenerator.reset();
        argsGenerator.wrap(argsBuffer, 0, argsBuffer.capacity());

        fieldId = null;
        fieldModel = null;
        fieldRole = null;
        fieldInputTokens = -1;
        fieldBlockId = 0;
        fieldBlockType = null;
        fieldToolId = null;
        fieldToolName = null;
        fieldContent = null;
        fieldToolCallArguments = null;
        fieldFinishReason = null;
        fieldFinishReasonPresent = false;
        fieldOutputTokens = -1;
    }

    private static String orDefault(
        String value,
        String fallback)
    {
        return value != null ? value : fallback;
    }

    private static LlmCanonicalFinishReason finishReason(
        String stopReason)
    {
        LlmCanonicalFinishReason reason;
        if ("max_tokens".equals(stopReason))
        {
            reason = LlmCanonicalFinishReason.LENGTH;
        }
        else if (TOOL_USE.equals(stopReason))
        {
            reason = LlmCanonicalFinishReason.TOOL_CALL;
        }
        else
        {
            reason = LlmCanonicalFinishReason.STOP;
        }
        return reason;
    }

    private static final class ContentBlock
    {
        private LlmCanonicalBlockKind kind;
        private String toolId;
        private String toolName;
        private String text;
    }
}
