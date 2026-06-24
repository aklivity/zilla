/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.avro.internal;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.runtime.common.avro.AvroDiagnostic;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream write transform session vended by AvroModelHandlerImpl: owns its own schema-keyed pipeline
// cache. transform emits the catalog framing prefix into the destination on the first fragment, then drives
// the common-avro transform (JSON in, Avro binary out) into the destination after it.
final class AvroEncodeModelPipeline implements ModelPipeline
{
    private final AvroModelHandlerImpl handler;
    private final Int2ObjectCache<AvroPipeline> pipelines;
    private final ModelPipelineResult result;

    private AvroPipeline active;
    private String diagnostic;
    private MutableDirectBuffer prefixBuffer;
    private int prefixAt;

    AvroEncodeModelPipeline(
        AvroModelHandlerImpl handler)
    {
        this.handler = handler;
        this.pipelines = new Int2ObjectCache<>(1, 16, p -> {});
        this.result = new ModelPipelineResult();
    }

    @Override
    public ModelPipelineResult transform(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer src,
        int srcIndex,
        int srcLimit,
        MutableDirectBuffer dst,
        int dstIndex,
        int dstLimit)
    {
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
            handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : AvroModel.NAME);
            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else
        {
            boolean last = (flags & FLAGS_FIN) != 0;
            AvroPipelineResult avro =
                active.transform(src, srcIndex, srcIndex + srcLength, last, dst, dstIndex + prefix, dstIndex + dstLength);
            status = map(avro.status());
            consumed = avro.consumed();
            produced = prefix + avro.produced();
            if (status == ModelStatus.REJECTED)
            {
                handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : AvroModel.NAME);
            }
        }
        return result.set(status, consumed, produced);
    }

    @Override
    public int padding(
        DirectBuffer data,
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
        DirectBuffer src,
        int srcIndex,
        int srcLength,
        MutableDirectBuffer dst,
        int dstIndex)
    {
        prefixBuffer = dst;
        prefixAt = dstIndex;
        handler.encodePrefix(traceId, bindingId, schemaId, src, srcIndex, srcLength, this::putPrefix);
        return prefixAt - dstIndex;
    }

    private void putPrefix(
        DirectBuffer buffer,
        int index,
        int length)
    {
        prefixBuffer.putBytes(prefixAt, buffer, index, length);
        prefixAt += length;
    }

    private AvroPipeline supplyPipeline(
        int schemaId)
    {
        return pipelines.computeIfAbsent(schemaId, id -> handler.newPipeline(id, this::onRejected));
    }

    private void onRejected(
        AvroDiagnostic diagnostic)
    {
        this.diagnostic = diagnostic.message();
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
