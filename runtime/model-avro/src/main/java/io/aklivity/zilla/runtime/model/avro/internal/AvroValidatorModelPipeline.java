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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

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

// Per-stream validate-only session vended by AvroValidatorModelHandler: drives the common-avro transform
// into a shared per-worker scratch buffer whose output is discarded, reporting only progress and outcome.
// The caller forwards the original src on success, so produced is always zero. Transitional — this
// re-serialize-to-validate path is removed once callers validate through the ModelPipeline contract.
final class AvroValidatorModelPipeline implements ModelPipeline
{
    private final AvroValidatorModelHandler handler;
    private final MutableDirectBuffer scratch;
    private final int scratchCapacity;
    private final Int2ObjectCache<AvroPipeline> pipelines;
    private final ModelPipelineResult result;

    private AvroPipeline active;
    private String diagnostic;

    AvroValidatorModelPipeline(
        AvroValidatorModelHandler handler,
        MutableDirectBuffer scratch)
    {
        this.handler = handler;
        this.scratch = scratch;
        this.scratchCapacity = scratch.capacity();
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
        int srcLength,
        MutableDirectBuffer dst,
        int dstIndex,
        int dstLength)
    {
        int prefix = 0;
        if ((flags & FLAGS_INIT) != 0)
        {
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
        if (active == null)
        {
            handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : AvroModel.NAME);
            status = ModelStatus.REJECTED;
            consumed = 0;
        }
        else
        {
            boolean last = (flags & FLAGS_FIN) != 0;
            int limit = srcIndex + srcLength;
            int payload = 0;
            // validation yields no output to the caller; drive the transform into the shared scratch and
            // drain it on overflow until the value resolves, advancing the source by the bytes consumed
            AvroPipelineResult avro;
            do
            {
                avro = active.transform(src, srcIndex + prefix + payload, limit, last, scratch, 0, scratchCapacity);
                payload += avro.consumed();
            }
            while (avro.status() == Status.SUSPENDED);
            status = map(avro.status());
            consumed = prefix + payload;
            if (status == ModelStatus.REJECTED)
            {
                handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : AvroModel.NAME);
            }
        }
        return result.set(status, consumed, 0);
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
