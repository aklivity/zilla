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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmErrorFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmUsageFW;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

final class LlmAnthropicResponseExtractor implements JsonTransform
{
    private static final int NONE = -1;

    private final StringBuilder path;
    private final int[] pathLengthAt;
    private final Mediator mediator;

    private JsonController upstream;
    private int depth;
    private String pendingKey;

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

    private final LlmResponseOptions responseOptions;

    LlmAnthropicResponseExtractor()
    {
        this.responseOptions = LlmAnthropicOptions.response();
        this.path = new StringBuilder();
        this.pathLengthAt = new int[16];
        this.mediator = new Mediator();
    }

    boolean failed()
    {
        return errorType != null || errorMessage != null;
    }

    String errorType()
    {
        return errorType;
    }

    String errorMessage()
    {
        return errorMessage;
    }

    LlmResponseOptions options()
    {
        return responseOptions;
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

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstream = control;

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            onContainerStart();
            break;
        case END_OBJECT:
        case END_ARRAY:
            onContainerEnd();
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
        case END_DOCUMENT:
            onDocumentEnd();
            break;
        default:
            break;
        }

        return sink.transform(mediator, source, event);
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstream = control;
        return sink.resume(mediator, source, event);
    }

    @Override
    public Status flush(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        upstream = control;
        return sink.flush(mediator, source);
    }

    @Override
    public void reset()
    {
        path.setLength(0);
        depth = 0;
        pendingKey = null;
        responseOptions.clear();
        clearDocument();
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    @Override
    public boolean deterministic()
    {
        return true;
    }

    private void onContainerStart()
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
    }

    private void onContainerEnd()
    {
        path.setLength(pathLengthAt[--depth]);

        if (depth == 0)
        {
            onDocumentEnd();
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
            if (source.deferredBytes())
            {
                control.consumed(0);
            }
            else
            {
                final int fieldAt = path.length();

                if (fieldAt > 0)
                {
                    path.append('.');
                }
                path.append(pendingKey);

                onField(path.toString(), source, event);

                path.setLength(fieldAt);
                pendingKey = null;
            }
        }
    }

    private void onField(
        String fieldPath,
        JsonSource source,
        JsonEvent event)
    {
        switch (event)
        {
        case VALUE_NUMBER:
            onNumber(fieldPath, source);
            break;
        case VALUE_STRING:
            onString(fieldPath, source);
            break;
        default:
            break;
        }
    }

    private void onString(
        String fieldPath,
        JsonSource source)
    {
        switch (fieldPath)
        {
        case "error.type":
            documentErrorType = source.getString();
            break;
        case "error.message":
            documentErrorMessage = source.getString();
            break;
        case "id":
        case "message.id":
            documentId = source.getString();
            break;
        case "model":
        case "message.model":
            documentModel = source.getString();
            break;
        case "stop_reason":
        case "delta.stop_reason":
            documentFinishReason = source.getString();
            break;
        case "stop_sequence":
        case "delta.stop_sequence":
            responseOptions.set(LlmAnthropicOptions.RESPONSE_STOP_SEQUENCE, LlmJsonLiteral.quote(source.getString()));
            break;
        default:
            break;
        }
    }

    private void onNumber(
        String fieldPath,
        JsonSource source)
    {
        switch (fieldPath)
        {
        case "message.usage.input_tokens":
        case "usage.input_tokens":
            documentInputTokens = source.getInt();
            break;
        case "message.usage.cache_creation_input_tokens":
        case "usage.cache_creation_input_tokens":
            documentCacheWriteTokens = source.getInt();
            break;
        case "message.usage.cache_read_input_tokens":
        case "usage.cache_read_input_tokens":
            documentCacheReadTokens = source.getInt();
            break;
        case "usage.output_tokens":
            documentOutputTokens = source.getInt();
            break;
        default:
            break;
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
        clearDocument();
    }

    private void clearDocument()
    {
        documentInputTokens = NONE;
        documentOutputTokens = NONE;
        documentCacheWriteTokens = NONE;
        documentCacheReadTokens = NONE;
        documentErrorType = null;
        documentErrorMessage = null;
        documentId = null;
        documentModel = null;
        documentFinishReason = null;
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

    private final class Mediator implements JsonController
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            upstream.consumed(sourceBytes);
        }
    }
}
