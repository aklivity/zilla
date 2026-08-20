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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.util.HashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufDiagnostic;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnvelope;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelFieldBridge;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// Per-stream read transform session vended by ProtobufModelHandlerImpl: owns its own extractor and
// message-keyed pipeline cache so concurrent streams on a worker never share in-flight state. transform
// strips the catalog framing and the message-index prefix on the first fragment, drives the common-protobuf
// transform into the caller's destination (re-encoding the wire message as JSON or canonical wire), and
// surfaces extracted fields to the wired ModelTransform when a value completes. Delivery is
// observation-only, through ModelFieldBridge, until model-protobuf grows a native ModelTransform adapter.
final class ProtobufModelDecoderPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private final ProtobufModelHandlerImpl handler;
    private final ModelFieldBridge bridge;
    private final ProtobufExtractor extractor;
    private final Map<String, ProtobufPipeline> pipelines;
    private final ProtobufEnvelope envelope;
    private final ModelPipelineResult result;

    private ProtobufPipeline active;
    private String diagnostic;

    ProtobufModelDecoderPipeline(
        ProtobufModelHandlerImpl handler,
        ProtobufEnvelope envelope,
        ModelTransform transform)
    {
        this.envelope = envelope;
        this.handler = handler;
        this.bridge = transform != ModelTransform.NONE ? new ModelFieldBridge(transform) : null;
        // a NONE transform keeps the verbatim/SEGMENTED fast path: no extractor stage, no structured field events
        this.extractor = transform != ModelTransform.NONE ? new ProtobufExtractor() : null;
        this.pipelines = new HashMap<>();
        this.result = new ModelPipelineResult();
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
        int srcLength = srcLimit - srcIndex;
        int dstLength = dstLimit - dstIndex;
        int prefix = 0;
        if ((flags & FLAGS_INIT) != 0)
        {
            // the catalog framing then the message-index prefix sit at the value start; strip both once on the
            // first fragment and select the schema-bound pipeline, then later fragments stream straight through
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
        int produced;
        if (active == null)
        {
            handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : ProtobufModel.NAME);
            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else
        {
            active.authorization(authorization);
            boolean last = (flags & FLAGS_FIN) != 0;
            ProtobufPipelineResult proto =
                active.transform(src, srcIndex + prefix, srcIndex + srcLength, last, dst, dstIndex, dstIndex + dstLength);
            status = map(proto.status());
            consumed = prefix + proto.consumed();
            produced = proto.produced();
            if (status == ModelStatus.COMPLETE && extractor != null)
            {
                visitExtracted(authorization);
            }
            else if (status == ModelStatus.REJECTED)
            {
                handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : ProtobufModel.NAME);
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

    private void visitExtracted(
        long authorization)
    {
        bridge.start(authorization);
        for (int i = 0; i < extractor.captured(); i++)
        {
            bridge.field(extractor.path(i), extractor.value(i), 0, extractor.length(i));
        }
        bridge.end();
    }

    // called on every message (not just once per messageName), so this checks the cache directly rather
    // than through computeIfAbsent: that would evaluate the capturing "name -> ..." lambda as an argument
    // on every call regardless of whether the map already has an entry, allocating it for nothing on the
    // (overwhelmingly common) cache-hit path
    private ProtobufPipeline supplyPipeline(
        int schemaId,
        String messageName)
    {
        ProtobufPipeline pipeline = pipelines.get(messageName);
        if (pipeline == null)
        {
            pipeline = handler.newPipeline(schemaId, handler.decodeLenient, messageName, extractor, this::onRejected, envelope);
            pipelines.put(messageName, pipeline);
        }
        return pipeline;
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
