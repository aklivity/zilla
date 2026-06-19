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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufDiagnostic;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream validate-only session vended by ProtobufValidatorModelHandler: drives the common-protobuf
// transform into a shared per-worker scratch buffer whose output is discarded, reporting only progress and
// outcome. The caller forwards the original src on success, so produced is always zero. Transitional — this
// re-serialize-to-validate path is removed once callers validate through the ModelPipeline contract.
final class ProtobufValidatorModelPipeline implements ModelPipeline
{
    private final ProtobufValidatorModelHandler handler;
    private final MutableDirectBuffer scratch;
    private final int scratchCapacity;
    private final Map<String, ProtobufPipeline> pipelines;
    private final ModelPipelineResult result;

    private ProtobufPipeline active;
    private String diagnostic;

    ProtobufValidatorModelPipeline(
        ProtobufValidatorModelHandler handler,
        MutableDirectBuffer scratch)
    {
        this.handler = handler;
        this.scratch = scratch;
        this.scratchCapacity = scratch.capacity();
        this.pipelines = new HashMap<>();
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
            int catalogPrefix = handler.prefix(src, srcIndex, srcLength);
            int schemaId = handler.resolveSchemaId(src, srcIndex, srcLength);
            int indexProgress = handler.messageProgress(src, srcIndex + catalogPrefix, srcLength - catalogPrefix);
            ProtobufMessage message = handler.message(schemaId);
            prefix = catalogPrefix + indexProgress;
            active = schemaId != NO_SCHEMA_ID && message != null ? supplyPipeline(schemaId, message.name()) : null;
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
            handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : ProtobufModel.NAME);
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
            ProtobufPipelineResult proto;
            do
            {
                proto = active.transform(src, srcIndex + prefix + payload, limit, last, scratch, 0, scratchCapacity);
                payload += proto.consumed();
            }
            while (proto.status() == Status.SUSPENDED);
            status = map(proto.status());
            consumed = prefix + payload;
            if (status == ModelStatus.REJECTED)
            {
                handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : ProtobufModel.NAME);
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

    private ProtobufPipeline supplyPipeline(
        int schemaId,
        String messageName)
    {
        return pipelines.computeIfAbsent(messageName, name -> handler.newPipeline(schemaId, name, this::onRejected));
    }

    private void onRejected(
        ProtobufDiagnostic diagnostic)
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
