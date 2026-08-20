/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public final class KafkaCacheModel
{
    public static final KafkaCacheModel NONE = new KafkaCacheModel();

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    @FunctionalInterface
    public interface Output
    {
        void accept(
            DirectBufferEx buffer,
            int index,
            int length);
    }

    private final ModelPipeline pipeline;
    private final MutableDirectBufferEx scratch;

    public static KafkaCacheModel decoder(
        ModelHandler handler,
        ModelTransform transform,
        MutableDirectBufferEx scratch)
    {
        return handler != null
            ? new KafkaCacheModel(handler.supplyDecoder(ModelEnvelope.NONE, transform), scratch)
            : NONE;
    }

    public static KafkaCacheModel encoder(
        ModelHandler handler,
        MutableDirectBufferEx scratch)
    {
        return handler != null
            ? new KafkaCacheModel(handler.supplyEncoder(ModelEnvelope.NONE, ModelTransform.NONE), scratch)
            : NONE;
    }

    private KafkaCacheModel()
    {
        this.pipeline = null;
        this.scratch = null;
    }

    KafkaCacheModel(
        ModelPipeline pipeline,
        MutableDirectBufferEx scratch)
    {
        this.pipeline = pipeline;
        this.scratch = scratch;
    }

    public int transform(
        long traceId,
        long bindingId,
        long authorization,
        DirectBufferEx data,
        int index,
        int limit,
        Output next)
    {
        int total;
        if (pipeline == null)
        {
            final int length = limit - index;
            next.accept(data, index, length);
            total = length;
        }
        else
        {
            total = 0;
            int srcAt = index;
            int flags = FLAGS_INIT | FLAGS_FIN;
            boolean done = false;
            while (!done)
            {
                final ModelPipelineResult result = pipeline.transform(traceId, bindingId, authorization, flags,
                    data, srcAt, limit, scratch, 0, scratch.capacity());
                final ModelStatus status = result.status();
                final int produced = result.produced();
                final int consumed = result.consumed();

                if (status == ModelStatus.REJECTED)
                {
                    total = -1;
                    done = true;
                }
                else
                {
                    if (produced > 0)
                    {
                        next.accept(scratch, 0, produced);
                        total += produced;
                    }

                    if (status == ModelStatus.COMPLETE)
                    {
                        done = true;
                    }
                    else
                    {
                        srcAt += consumed;
                        flags = FLAGS_FIN;
                    }
                }
            }

            pipeline.reset();
        }
        return total;
    }

    public int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return pipeline != null ? pipeline.padding(data, index, length) : 0;
    }

    public void reset()
    {
        if (pipeline != null)
        {
            pipeline.reset();
        }
    }
}
