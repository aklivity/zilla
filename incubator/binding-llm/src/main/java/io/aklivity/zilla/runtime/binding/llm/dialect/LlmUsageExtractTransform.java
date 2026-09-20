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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Observes a response document's {@code usage} field(s), at whatever depth and under whatever dialect-native
 * path they occur, copying each recognized value into the supplied {@link JsonEnvelope} while forwarding
 * every field unchanged, at any depth: no canonical rewriting. Mirrors {@link LlmModelExtractTransform} and
 * a Kafka cache model's {@code extractKey}/{@code extractHeaders} transform, generalized from a single
 * depth-1 field to the nested, dialect-specific paths a native {@code usage} object occurs at.
 * <p>
 * Every value captured this document is only written to {@code envelope} once the whole document has been
 * seen ({@link JsonEvent#END_DOCUMENT}), so a partially-decoded number never reaches the envelope. Because
 * {@link JsonEnvelope#set(String, DirectBufferEx)} is repeatable per name, a dialect that reports usage
 * across more than one document (e.g. Anthropic's {@code message_start}/{@code message_delta} pair) simply
 * adds a further occurrence each time one of its fields recurs -- a reader that takes the last value under
 * each name sees the latest-known figure without this transform needing to track cross-document state of
 * its own.
 * </p>
 */
abstract class LlmUsageExtractTransform implements JsonTransform
{
    static final String USAGE_INPUT_TOKENS = "usage.inputTokens";
    static final String USAGE_CACHE_WRITE_TOKENS = "usage.cacheWriteTokens";
    static final String USAGE_CACHE_READ_TOKENS = "usage.cacheReadTokens";
    static final String USAGE_OUTPUT_TOKENS = "usage.outputTokens";
    static final String USAGE_REASONING_TOKENS = "usage.reasoningTokens";
    static final String USAGE_TOTAL_TOKENS = "usage.totalTokens";

    private final JsonEnvelope envelope;
    private final StringBuilder path;
    private final int[] pathLengthAt;
    private final Mediator mediator;

    private JsonController upstream;
    private int depth;
    private String pendingKey;

    private int chunkInputTokens = -1;
    private int chunkCacheWriteTokens = -1;
    private int chunkCacheReadTokens = -1;
    private int chunkOutputTokens = -1;
    private int chunkReasoningTokens = -1;
    private int chunkTotalTokens = -1;

    LlmUsageExtractTransform(
        JsonEnvelope envelope)
    {
        this.envelope = envelope;
        this.path = new StringBuilder();
        this.pathLengthAt = new int[16];
        this.mediator = new Mediator();
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
        clearChunk();
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    /**
     * Observes the scalar at {@code fieldPath} (the dot-joined key path from the document root), captured
     * on {@link JsonEvent#END_DOCUMENT} only when one of the {@code usageXxx} setters below was called for
     * it. Called for every scalar in the document, not only usage fields -- an implementation matches its
     * own dialect's known paths and ignores everything else.
     */
    protected abstract void onField(
        String fieldPath,
        JsonSource source,
        JsonEvent event);

    protected final void inputTokens(
        int value)
    {
        chunkInputTokens = value;
    }

    protected final void cacheWriteTokens(
        int value)
    {
        chunkCacheWriteTokens = value;
    }

    protected final void cacheReadTokens(
        int value)
    {
        chunkCacheReadTokens = value;
    }

    protected final void outputTokens(
        int value)
    {
        chunkOutputTokens = value;
    }

    protected final void reasoningTokens(
        int value)
    {
        chunkReasoningTokens = value;
    }

    protected final void totalTokens(
        int value)
    {
        chunkTotalTokens = value;
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

    private void onDocumentEnd()
    {
        flush(USAGE_INPUT_TOKENS, chunkInputTokens);
        flush(USAGE_CACHE_WRITE_TOKENS, chunkCacheWriteTokens);
        flush(USAGE_CACHE_READ_TOKENS, chunkCacheReadTokens);
        flush(USAGE_OUTPUT_TOKENS, chunkOutputTokens);
        flush(USAGE_REASONING_TOKENS, chunkReasoningTokens);
        flush(USAGE_TOTAL_TOKENS, chunkTotalTokens);
        clearChunk();
    }

    private void flush(
        String name,
        int value)
    {
        if (value != -1)
        {
            envelope.set(name, asBuffer(Integer.toString(value)));
        }
    }

    private void clearChunk()
    {
        chunkInputTokens = -1;
        chunkCacheWriteTokens = -1;
        chunkCacheReadTokens = -1;
        chunkOutputTokens = -1;
        chunkReasoningTokens = -1;
        chunkTotalTokens = -1;
    }

    private static DirectBufferEx asBuffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    // Blocks the downstream's segmentable opt-in from reaching the upstream, which would substitute opaque
    // segments for the structure this stage needs to read field-by-field at arbitrary depth -- mirrors
    // LlmRequestFieldTransform.Mediator.
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
