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

final class LlmOpenaiResponseExtractor implements JsonTransform
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
    private int documentTotalTokens = NONE;
    private int documentCacheReadTokens = NONE;
    private int documentReasoningTokens = NONE;
    private String documentErrorType;
    private String documentErrorMessage;

    private int inputTokens = NONE;
    private int outputTokens = NONE;
    private int totalTokens = NONE;
    private int cacheReadTokens = NONE;
    private int reasoningTokens = NONE;
    private String errorType;
    private String errorMessage;

    LlmOpenaiResponseExtractor()
    {
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
        clearDocument();
    }

    @Override
    public boolean identity()
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
        case "usage.prompt_tokens":
            documentInputTokens = source.getInt();
            break;
        case "usage.completion_tokens":
            documentOutputTokens = source.getInt();
            break;
        case "usage.total_tokens":
            documentTotalTokens = source.getInt();
            break;
        case "usage.prompt_tokens_details.cached_tokens":
            documentCacheReadTokens = source.getInt();
            break;
        case "usage.completion_tokens_details.reasoning_tokens":
            documentReasoningTokens = source.getInt();
            break;
        default:
            break;
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

        clearDocument();
    }

    private void clearDocument()
    {
        documentInputTokens = NONE;
        documentOutputTokens = NONE;
        documentTotalTokens = NONE;
        documentCacheReadTokens = NONE;
        documentReasoningTokens = NONE;
        documentErrorType = null;
        documentErrorMessage = null;
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
