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
package io.aklivity.zilla.runtime.model.avro.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroDiagnostic;
import io.aklivity.zilla.runtime.common.avro.AvroEnvelope;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroPipelineResult;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// Per-stream read transform session vended by AvroModelHandlerImpl: owns its own JSON generator, per-field
// ModelTransform adapter and schema-keyed pipeline cache so concurrent streams on a worker never share
// in-flight state. transform strips the catalog framing on the first fragment and drives the common-avro
// transform into the caller's destination (re-encoding Avro as JSON or canonical Avro); the adapter presents
// each field to the wired ModelTransform inline, as the value flows through.
final class AvroModelDecoderPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private final AvroModelHandlerImpl handler;
    private final JsonGeneratorEx generator;
    private final AvroTransform adapter;
    private final AvroEnvelope envelope;
    private final Int2ObjectCache<AvroPipeline> pipelines;
    private final ModelPipelineResult result;
    private final ModelCache cache;

    private AvroPipeline active;
    private String diagnostic;
    private AvroDiagnostic.Category category;
    private MutableDirectBufferEx framingBuffer;
    private int framingAt;

    AvroModelDecoderPipeline(
        AvroModelHandlerImpl handler,
        AvroEnvelope envelope,
        ModelTransform transform,
        ModelCache cache)
    {
        this.handler = handler;
        this.envelope = envelope;
        this.generator = JsonEx.createGenerator();
        this.adapter = AvroModelTransform.of(transform);
        this.pipelines = new Int2ObjectCache<>(1, 16, p -> {});
        this.result = new ModelPipelineResult();
        this.cache = cache;
    }

    @Override
    public ModelPipelineResult transform(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        if (adapter instanceof AvroModelTransform mediating)
        {
            mediating.authorization(authorization);
        }

        int srcLength = srcLimit - srcIndex;
        int dstLength = dstLimit - dstIndex;
        int prefix = 0;
        int framing = 0;
        if ((flags & FLAGS_INIT) != 0)
        {
            // the catalog framing sits at the value start; strip it once on the first fragment and select
            // the schema-bound pipeline, then later fragments stream straight through
            int schemaId = handler.resolveSchemaId(src, srcIndex, srcLength);
            prefix = handler.prefix(src, srcIndex, srcLength);
            active = schemaId != NO_SCHEMA_ID ? supplyPipeline(schemaId) : null;
            if (active != null)
            {
                active.reset();
                if (cache == ModelCache.WRITE)
                {
                    // WRITE persists the resolved schema id as catalog framing ahead of its cached output,
                    // so a later READ of this cached value recovers it directly instead of falling back to
                    // static config resolution -- the case that matters is a per-message schema id (an
                    // encoded strategy), which a view-converting WRITE output would otherwise discard
                    framing = writeFraming(traceId, bindingId, schemaId, src, srcIndex, srcLength, dst, dstIndex);
                }
            }
            diagnostic = null;
            category = null;
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
            active.authorization(authorization);
            boolean last = (flags & FLAGS_FIN) != 0;
            AvroPipelineResult avro = active.transform(src, srcIndex + prefix, srcIndex + srcLength, last,
                dst, dstIndex + framing, dstIndex + dstLength);
            status = map(avro.status());
            consumed = prefix + avro.consumed();
            produced = framing + avro.produced();
            if (status == ModelStatus.REJECTED)
            {
                String reason = diagnostic != null ? diagnostic : AvroModel.NAME;
                switch (category)
                {
                case PARSING:
                    handler.parsingFailure(traceId, bindingId, reason);
                    break;
                case TRANSFORM:
                    handler.transformFailure(traceId, bindingId, reason);
                    break;
                case VALIDATION:
                default:
                    handler.validationFailure(traceId, bindingId, reason);
                    break;
                }
            }
        }
        return result.set(status, consumed, produced);
    }

    @Override
    public boolean identity()
    {
        return active != null && active.identity();
    }

    @Override
    public int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        int padding = handler.decodePadding(data, index, length);
        if (cache == ModelCache.WRITE)
        {
            padding += handler.framingPadding(length);
        }
        return padding;
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
        category = null;
    }

    private AvroPipeline supplyPipeline(
        int schemaId)
    {
        return pipelines.computeIfAbsent(schemaId,
            id -> handler.newPipeline(id, handler.decodeLenient, generator, adapter, this::onRejected, envelope, cache));
    }

    private int writeFraming(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBufferEx src,
        int srcIndex,
        int srcLength,
        MutableDirectBufferEx dst,
        int dstIndex)
    {
        framingBuffer = dst;
        framingAt = dstIndex;
        handler.encodePrefix(traceId, bindingId, schemaId, src, srcIndex, srcLength, this::putFraming);
        return framingAt - dstIndex;
    }

    private void putFraming(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        framingBuffer.putBytes(framingAt, buffer, index, length);
        framingAt += length;
    }

    private void onRejected(
        AvroDiagnostic diagnostic)
    {
        this.diagnostic = diagnostic.message();
        this.category = diagnostic.category();
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
