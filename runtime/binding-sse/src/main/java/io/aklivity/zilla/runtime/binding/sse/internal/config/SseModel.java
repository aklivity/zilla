/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.sse.internal.config;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

/**
 * Per-stream driver around a decode {@link ModelPipeline} for the sse binding.
 * <p>
 * An event payload is transformed whole-value via {@link #transform(long, long, DirectBuffer, int, int)}: the
 * value is driven through the pipeline and the produced (possibly changed) bytes are exposed via
 * {@link #buffer} for the caller to encode downstream, or {@code -1} signals the model rejected it.
 * </p>
 */
public final class SseModel
{
    public static final SseModel NONE = new SseModel();

    private final ModelPipeline pipeline;
    private final MutableDirectBuffer scratch;

    public static SseModel decoder(
        ModelHandler handler,
        MutableDirectBuffer scratch)
    {
        return handler != null
            ? new SseModel(handler.supplyDecoder(), scratch)
            : NONE;
    }

    private SseModel()
    {
        this.pipeline = null;
        this.scratch = null;
    }

    SseModel(
        ModelPipeline pipeline,
        MutableDirectBuffer scratch)
    {
        this.pipeline = pipeline;
        this.scratch = scratch;
    }

    public boolean active()
    {
        return pipeline != null;
    }

    public int transform(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int limit)
    {
        int total = 0;
        int srcAt = index;
        int flags = ModelPipeline.FLAGS_INIT | ModelPipeline.FLAGS_FIN;
        boolean done = false;
        while (!done)
        {
            final ModelPipelineResult result = pipeline.transform(traceId, bindingId, flags,
                data, srcAt, limit, scratch, total, scratch.capacity());
            final ModelStatus status = result.status();

            if (status == ModelStatus.REJECTED)
            {
                total = -1;
                done = true;
            }
            else
            {
                total += result.produced();

                if (status == ModelStatus.COMPLETE)
                {
                    done = true;
                }
                else
                {
                    srcAt += result.consumed();
                    flags = ModelPipeline.FLAGS_FIN;
                }
            }
        }

        pipeline.reset();
        return total;
    }

    public DirectBuffer buffer()
    {
        return scratch;
    }
}
