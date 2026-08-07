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
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroPipelineResult;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
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
    private final Int2ObjectCache<AvroPipeline> pipelines;
    private final ModelPipelineResult result;

    private AvroPipeline active;
    private String diagnostic;

    AvroModelDecoderPipeline(
        AvroModelHandlerImpl handler,
        ModelTransform transform)
    {
        this.handler = handler;
        this.generator = JsonEx.createGenerator();
        this.adapter = AvroModelTransform.of(transform);
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
        int srcLength = srcLimit - srcIndex;
        int dstLength = dstLimit - dstIndex;
        int prefix = 0;
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
            }
            diagnostic = null;
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
                active.transform(src, srcIndex + prefix, srcIndex + srcLength, last, dst, dstIndex, dstIndex + dstLength);
            status = map(avro.status());
            consumed = prefix + avro.consumed();
            produced = avro.produced();
            if (status == ModelStatus.REJECTED)
            {
                handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : AvroModel.NAME);
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
        return handler.decodePadding(data, index, length);
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

    private AvroPipeline supplyPipeline(
        int schemaId)
    {
        return pipelines.computeIfAbsent(schemaId,
            id -> handler.newPipeline(id, handler.decodeLenient, generator, adapter, this::onRejected));
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
