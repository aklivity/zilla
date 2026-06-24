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

// Per-stream write transform session vended by ProtobufModelHandlerImpl: owns its own message-keyed
// pipeline cache. transform emits the catalog framing then the message-index prefix into the destination on
// the first fragment, then drives the common-protobuf transform (JSON or wire in, wire out) into the
// destination after them.
final class ProtobufModelEncoderPipeline implements ModelPipeline
{
    private final ProtobufModelHandlerImpl handler;
    private final Map<String, ProtobufPipeline> pipelines;
    private final ModelPipelineResult result;

    private ProtobufPipeline active;
    private String diagnostic;
    private MutableDirectBuffer prefixBuffer;
    private int prefixAt;

    ProtobufModelEncoderPipeline(
        ProtobufModelHandlerImpl handler)
    {
        this.handler = handler;
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
            int[] path = handler.messagePath(schemaId);
            ProtobufMessage message = handler.message(schemaId, path);
            diagnostic = null;
            active = message != null ? supplyPipeline(schemaId, message.name()) : null;
            if (active != null)
            {
                active.reset();
                // the catalog framing and the message-index prefix are emitted once into the destination ahead
                // of the value
                prefix = writeFraming(traceId, bindingId, schemaId, path, src, srcIndex, srcLength, dst, dstIndex);
            }
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
            boolean last = (flags & FLAGS_FIN) != 0;
            ProtobufPipelineResult proto =
                active.transform(src, srcIndex, srcIndex + srcLength, last, dst, dstIndex + prefix, dstIndex + dstLength);
            status = map(proto.status());
            consumed = proto.consumed();
            produced = prefix + proto.produced();
            if (status == ModelStatus.REJECTED)
            {
                handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : ProtobufModel.NAME);
            }
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

    private int writeFraming(
        long traceId,
        long bindingId,
        int schemaId,
        int[] path,
        DirectBuffer src,
        int srcIndex,
        int srcLength,
        MutableDirectBuffer dst,
        int dstIndex)
    {
        prefixBuffer = dst;
        prefixAt = dstIndex;
        handler.encodePrefix(traceId, bindingId, schemaId, src, srcIndex, srcLength, this::putPrefix);
        byte[] framing = handler.indexFraming(path);
        prefixBuffer.putBytes(prefixAt, framing, 0, framing.length);
        prefixAt += framing.length;
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
