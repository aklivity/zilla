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

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Decodes Anthropic's native streaming event sequence into the canonical vocabulary, driven directly by
 * real {@link JsonEvent}s from a long-lived {@link io.aklivity.zilla.runtime.common.json.JsonPipeline} (one
 * instance per response stream, reused for every native chunk over the stream's lifetime -- see
 * {@link LlmCanonicalEmitter}). Which native SSE {@code event:} name selects which decode behavior is keyed
 * by the out-of-band event name set via {@link #event(String)} before each chunk is fed to the pipeline --
 * that dispatch key arrives from SSE framing, not from the JSON body, so it is not itself something a
 * {@code JsonTransform} observes as an event.
 * <p>
 * Rather than walking the (often trivial) native body field-by-field before deciding what to queue, this
 * waits for the native chunk's own real {@code END_DOCUMENT} -- exactly like {@link LlmOpenaiDecodeTransform}
 * -- so both dialects share one fan-out timing rule; only {@code message_start}/{@code content_block_start}/
 * {@code content_block_delta}/{@code message_delta} actually read any fields from the body via the shallow
 * dotted-path accumulation below (none of this dialect's needed fields sit inside an array, so a depth/path
 * tracker is all that is needed -- no index bookkeeping).
 * <p>
 * {@code inputTokens}/{@code openBlockType}/{@code openBlockId} live for the whole response stream, across
 * every native chunk -- {@link #reset()} is never called between chunks under normal operation (see
 * {@code LlmClientFactory}'s document-boundary policy: {@code nextDocument()} advances between chunks
 * without cascading a reset to this stage), so these fields need no special preservation.
 */
final class LlmAnthropicDecodeTransform extends LlmCanonicalEmitter implements LlmDialectEvent
{
    private static final String MESSAGE_START = "message_start";
    private static final String CONTENT_BLOCK_START = "content_block_start";
    private static final String CONTENT_BLOCK_DELTA = "content_block_delta";
    private static final String CONTENT_BLOCK_STOP = "content_block_stop";
    private static final String MESSAGE_DELTA = "message_delta";
    private static final String MESSAGE_STOP = "message_stop";

    private int inputTokens = -1;
    private LlmCanonicalBlockKind openBlockType;
    private int openBlockId;

    private String nativeEvent;

    private final StringBuilder path;
    private final int[] pathLengthAt;
    private int depth;
    private String pendingKey;

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

    // Walks the native event's whole document, tracking the dotted path of object keys leading to the
    // current scalar (root-level fields have no dot) -- see LlmCanonicalEmitter's javadoc for why this
    // stays shallow rather than reproducing OpenAI's index-tracking walk.
    private void onEvent(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
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
            break;
        case END_OBJECT:
        case END_ARRAY:
            path.setLength(pathLengthAt[--depth]);
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
        default:
            break;
        }
    }

    private void onDocumentEnd()
    {
        if (nativeEvent == null)
        {
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
        boolean toolCall = "tool_use".equals(fieldBlockType);
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

    private void clearFields()
    {
        nativeEvent = null;
        path.setLength(0);
        depth = 0;
        pendingKey = null;

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
        else if ("tool_use".equals(stopReason))
        {
            reason = LlmCanonicalFinishReason.TOOL_CALL;
        }
        else
        {
            reason = LlmCanonicalFinishReason.STOP;
        }
        return reason;
    }
}
