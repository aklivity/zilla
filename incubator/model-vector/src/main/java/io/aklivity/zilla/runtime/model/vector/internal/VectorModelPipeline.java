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
package io.aklivity.zilla.runtime.model.vector.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream whole-value transform for the vector model: buffers a value's bytes until the final
// fragment, then embeds the accumulated text and suspends until that embedding resolves. The pipeline
// never rewrites accepted bytes (identity() is true), so the accumulated buffer doubles as the source
// for the accepted-and-draining phase once resolved.
final class VectorModelPipeline implements ModelPipeline
{
    private static final int FLAGS_FIN = 0x01;

    private final VectorModelHandlerImpl handler;
    private final Runnable resumed;
    private final ExpandableArrayBufferEx buffer;
    private final ModelPipelineResult result;

    private int length;
    private int drained;
    private boolean awaiting;
    private ModelStatus resolved;
    private long generation;

    VectorModelPipeline(
        VectorModelHandlerImpl handler,
        Runnable resumed)
    {
        this.handler = handler;
        this.resumed = resumed;
        this.buffer = new ExpandableArrayBufferEx();
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
        ModelStatus status;
        int consumed = 0;
        int produced = 0;

        if (resolved == ModelStatus.REJECTED)
        {
            status = ModelStatus.REJECTED;
        }
        else if (resolved == ModelStatus.OK)
        {
            int remaining = length - drained;
            int available = Math.min(remaining, dstLimit - dstIndex);
            dst.putBytes(dstIndex, buffer, drained, available);
            drained += available;
            produced = available;
            status = drained < length ? ModelStatus.OVERFLOW : ModelStatus.COMPLETE;
        }
        else if (awaiting)
        {
            status = ModelStatus.SUSPENDED;
        }
        else
        {
            int available = srcLimit - srcIndex;
            buffer.putBytes(length, src, srcIndex, available);
            length += available;
            consumed = available;

            if ((flags & FLAGS_FIN) != 0)
            {
                awaiting = true;
                long thisGeneration = ++generation;
                String text = buffer.getStringWithoutLengthUtf8(0, length);
                handler.whenReady(() -> embed(traceId, bindingId, thisGeneration, text));
                status = ModelStatus.SUSPENDED;
            }
            else
            {
                status = ModelStatus.UNDERFLOW;
            }
        }

        return result.set(status, consumed, produced);
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    @Override
    public void reset()
    {
        length = 0;
        drained = 0;
        awaiting = false;
        resolved = null;
        generation++;
    }

    private void embed(
        long traceId,
        long bindingId,
        long expectedGeneration,
        String text)
    {
        handler.embed(traceId, bindingId, text, new EmbeddingHandler.CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                float[] vector)
            {
                onEmbedded(expectedGeneration, handler.matches(vector));
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                onEmbedded(expectedGeneration, false);
            }
        });
    }

    private void onEmbedded(
        long expectedGeneration,
        boolean rejected)
    {
        if (expectedGeneration == generation && awaiting)
        {
            resolved = rejected ? ModelStatus.REJECTED : ModelStatus.OK;
            awaiting = false;
            resumed.run();
        }
    }
}
