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

import java.util.Set;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;

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
    private final KafkaExtractor extractor;
    private final MutableDirectBufferEx scratch;

    public static KafkaCacheModel decoder(
        ModelHandler handler,
        Set<String> extractPaths,
        MutableDirectBufferEx scratch)
    {
        KafkaCacheModel model = NONE;
        if (handler != null)
        {
            if (extractPaths != null && !extractPaths.isEmpty())
            {
                KafkaExtractor extractor = new KafkaExtractor(extractPaths);
                model = new KafkaCacheModel(handler.supplyDecoder(extractor), extractor, scratch);
            }
            else
            {
                model = new KafkaCacheModel(handler.supplyDecoder(), null, scratch);
            }
        }
        return model;
    }

    public static KafkaCacheModel encoder(
        ModelHandler handler,
        MutableDirectBufferEx scratch)
    {
        return handler != null
            ? new KafkaCacheModel(handler.supplyEncoder(), null, scratch)
            : NONE;
    }

    private KafkaCacheModel()
    {
        this.pipeline = null;
        this.extractor = null;
        this.scratch = null;
    }

    KafkaCacheModel(
        ModelPipeline pipeline,
        KafkaExtractor extractor,
        MutableDirectBufferEx scratch)
    {
        this.pipeline = pipeline;
        this.extractor = extractor;
        this.scratch = scratch;
    }

    public int transform(
        long traceId,
        long bindingId,
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
            if (extractor != null)
            {
                extractor.reset();
            }

            total = 0;
            int srcAt = index;
            int flags = FLAGS_INIT | FLAGS_FIN;
            boolean done = false;
            while (!done)
            {
                final ModelPipelineResult result = pipeline.transform(traceId, bindingId, flags,
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

    public int extractedLength(
        String path)
    {
        return extractor != null ? extractor.extractedLength(path) : 0;
    }

    public void extracted(
        String path,
        ModelVisitor visitor)
    {
        if (extractor != null)
        {
            extractor.extracted(path, visitor);
        }
    }

    public void reset()
    {
        if (pipeline != null)
        {
            pipeline.reset();
        }

        if (extractor != null)
        {
            extractor.reset();
        }
    }
}
