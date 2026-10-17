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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.util.Flags;

final class LlmRawRunPipeline implements LlmRunPipeline
{
    private static final DirectBufferEx EMPTY = new UnsafeBufferEx(new byte[0]);

    private final ModelPipeline pipeline;

    private boolean started;

    LlmRawRunPipeline(
        ModelPipeline pipeline)
    {
        this.pipeline = pipeline;
    }

    @Override
    public void reset()
    {
        pipeline.reset();
        started = false;
    }

    @Override
    public ModelPipelineResult accept(
        long traceId,
        long bindingId,
        long authorization,
        boolean sectionInit,
        boolean sectionFin,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final ModelPipelineResult result = pipeline.transform(traceId, bindingId, authorization, flags(0), src, srcIndex,
            srcLimit, dst, dstIndex, dstLimit);

        started = true;

        return result;
    }

    @Override
    public ModelPipelineResult finish(
        long traceId,
        long bindingId,
        long authorization,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final ModelPipelineResult result = pipeline.transform(traceId, bindingId, authorization, flags(Flags.FIN), EMPTY, 0, 0,
            dst, dstIndex, dstLimit);

        started = true;

        return result;
    }

    @Override
    public int closeWidth()
    {
        return 0;
    }

    @Override
    public int close(
        MutableDirectBufferEx dst,
        int dstIndex)
    {
        return 0;
    }

    private int flags(
        int flags)
    {
        return started ? flags : flags | Flags.INIT;
    }
}
