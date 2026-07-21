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
package io.aklivity.zilla.runtime.model.json.internal;

import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream write transform session vended by JsonModelHandlerImpl: owns its own generator and
// schema-keyed pipeline cache. transform emits the catalog framing prefix into the destination on the
// first fragment, then drives the common-json transform into the destination after it.
final class JsonModelEncoderPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private final JsonModelHandlerImpl handler;
    private final JsonGeneratorEx generator;
    private final Int2ObjectCache<JsonPipeline> pipelines;
    private final ModelPipelineResult result;

    private JsonPipeline active;
    private String diagnostic;
    private MutableDirectBufferEx prefixBuffer;
    private int prefixAt;
    // captured per transform call so the JsonReporter callback can fire the validation-failed event with
    // the right trace and binding even when the value is tolerated (LENIENT) rather than rejected
    private long traceId;
    private long bindingId;

    JsonModelEncoderPipeline(
        JsonModelHandlerImpl handler)
    {
        this.handler = handler;
        this.generator = JsonEx.createGenerator();
        this.pipelines = new Int2ObjectCache<>(1, 16, p -> {});
        this.result = new ModelPipelineResult();
    }

    @Override
    public ModelPipelineResult transform(
        long traceId,
        long bindingId,
        int flags,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        this.traceId = traceId;
        this.bindingId = bindingId;
        int srcLength = srcLimit - srcIndex;
        int dstLength = dstLimit - dstIndex;
        int prefix = 0;
        if ((flags & FLAGS_INIT) != 0)
        {
            int schemaId = handler.resolveSchemaId();
            active = supplyPipeline(schemaId);
            diagnostic = null;
            if (active != null)
            {
                active.reset();
                // the schema framing prefix is emitted once into the destination ahead of the value
                prefix = writePrefix(traceId, bindingId, schemaId, src, srcIndex, srcLength, dst, dstIndex);
            }
        }

        ModelStatus status;
        int consumed;
        int produced;
        if (active == null)
        {
            // no schema resolved: the value cannot be validated at all, so reject (the JsonReporter never
            // fires here, so emit the event directly)
            handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : JsonModel.NAME);
            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else
        {
            boolean last = (flags & FLAGS_FIN) != 0;
            JsonPipelineResult json =
                active.transform(src, srcIndex, srcIndex + srcLength, last, dst, dstIndex + prefix, dstIndex + dstLength);
            status = map(json.status());
            consumed = json.consumed();
            produced = prefix + json.produced();
            // a parse or schema-validation failure already fired the validation-failed event via onRejected;
            // under LENIENT the value still completes (original bytes passed through) and the event still fired
        }
        return result.set(status, consumed, produced);
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return handler.encodePadding(length);
    }

    @Override
    public void reset()
    {
        if (active != null)
        {
            active.reset();
        }
        active = null;
        diagnostic = null;
    }

    private int writePrefix(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBufferEx src,
        int srcIndex,
        int srcLength,
        MutableDirectBufferEx dst,
        int dstIndex)
    {
        prefixBuffer = dst;
        prefixAt = dstIndex;
        handler.encodePrefix(traceId, bindingId, schemaId, src, srcIndex, srcLength, this::putPrefix);
        return prefixAt - dstIndex;
    }

    private void putPrefix(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        prefixBuffer.putBytes(prefixAt, buffer, index, length);
        prefixAt += length;
    }

    private JsonPipeline supplyPipeline(
        int schemaId)
    {
        return pipelines.computeIfAbsent(schemaId,
            id -> handler.newPipeline(id, handler.encodeLenient, generator, this::onRejected));
    }

    private void onRejected(
        JsonDiagnostic diagnostic)
    {
        // fires once per parse or schema-validation failure, at the value boundary, in both STRICT and
        // LENIENT — so the validation-failed event is always emitted even when LENIENT tolerates the value
        this.diagnostic = diagnostic.message();
        handler.validationFailure(traceId, bindingId, this.diagnostic != null ? this.diagnostic : JsonModel.NAME);
    }

    private static ModelStatus map(
        Status status)
    {
        return switch (status)
        {
        case COMPLETED -> ModelStatus.COMPLETE;
        case SUSPENDED -> ModelStatus.OVERFLOW;
        case STARVED -> ModelStatus.UNDERFLOW;
        case REJECTED -> ModelStatus.REJECTED;
        default -> ModelStatus.OK;
        };
    }
}
