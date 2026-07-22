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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

/**
 * Per-stream driver around a decode {@link ModelPipeline} for the http binding.
 * <p>
 * Scalar metadata (header, path and query values) is transformed whole-value via the
 * {@link #transform(long, long, DirectBufferEx, int, int)} overload: the value is driven through the
 * pipeline and the produced (possibly changed) bytes are exposed via {@link #buffer} / {@link #produced}
 * for the caller to substitute downstream, or {@code -1} signals the model rejected it. Streaming content
 * is transformed via {@link #transform(long, long, int, DirectBufferEx, int, int, int)}: each fragment is
 * driven through the pipeline and the produced bytes are exposed for the caller to forward downstream.
 * </p>
 */
public final class HttpModel
{
    public static final HttpModel NONE = new HttpModel();

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private final ModelPipeline pipeline;
    private final MutableDirectBufferEx scratch;

    private int produced;

    public static HttpModel decoder(
        ModelHandler handler,
        MutableDirectBufferEx scratch)
    {
        return handler != null
            ? new HttpModel(handler.supplyDecoder(), scratch)
            : NONE;
    }

    private HttpModel()
    {
        this.pipeline = null;
        this.scratch = null;
    }

    HttpModel(
        ModelPipeline pipeline,
        MutableDirectBufferEx scratch)
    {
        this.pipeline = pipeline;
        this.scratch = scratch;
    }

    public int transform(
        long traceId,
        long bindingId,
        DirectBufferEx data,
        int index,
        int limit)
    {
        int total;
        if (pipeline == null)
        {
            final int length = limit - index;
            scratch.putBytes(0, data, index, length);
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
                        flags = FLAGS_FIN;
                    }
                }
            }

            pipeline.reset();
        }
        return total;
    }

    public int transform(
        long traceId,
        long bindingId,
        int flags,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        int dstMax)
    {
        final ModelPipelineResult result = pipeline.transform(traceId, bindingId, flags,
            src, srcIndex, srcLimit, scratch, 0, dstMax);
        final ModelStatus status = result.status();

        int consumed;
        if (status == ModelStatus.REJECTED)
        {
            produced = 0;
            consumed = -1;
        }
        else
        {
            produced = result.produced();
            consumed = result.consumed();
            if (status == ModelStatus.COMPLETE)
            {
                pipeline.reset();
            }
        }
        return consumed;
    }

    public DirectBufferEx buffer()
    {
        return scratch;
    }

    public int produced()
    {
        return produced;
    }
}
