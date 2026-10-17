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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

public class LlmRawRunPipelineTest
{
    @Test
    public void shouldDeliverInitOnceAndFinOnFinish()
    {
        final Recording recording = new Recording();
        final LlmRawRunPipeline pipeline = new LlmRawRunPipeline(recording);
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);
        final UnsafeBufferEx src = new UnsafeBufferEx(new byte[] {1, 2, 3});

        pipeline.accept(0L, 0L, 0L, true, false, src, 0, 3, dst, 0, 16);
        pipeline.accept(0L, 0L, 0L, false, true, src, 0, 3, dst, 0, 16);
        final ModelPipelineResult result = pipeline.finish(0L, 0L, 0L, dst, 0, 16);

        assertThat(recording.flags, equalTo(List.of(Flags.INIT, 0, Flags.FIN)));
        assertThat(result.status(), is(ModelStatus.COMPLETE));
        assertThat(pipeline.closeWidth(), is(0));
        assertThat(pipeline.close(dst, 0), is(0));
    }

    @Test
    public void shouldDeliverInitAgainAfterReset()
    {
        final Recording recording = new Recording();
        final LlmRawRunPipeline pipeline = new LlmRawRunPipeline(recording);
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);
        final UnsafeBufferEx src = new UnsafeBufferEx(new byte[] {1});

        pipeline.accept(0L, 0L, 0L, true, true, src, 0, 1, dst, 0, 16);
        pipeline.reset();
        pipeline.accept(0L, 0L, 0L, true, true, src, 0, 1, dst, 0, 16);

        assertThat(recording.flags, equalTo(List.of(Flags.INIT, Flags.INIT)));
        assertThat(pipeline.identity(), is(true));
    }

    private static final class Recording implements ModelPipeline
    {
        private final List<Integer> flags = new ArrayList<>();
        private final ModelPipelineResult result = new ModelPipelineResult();

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
            this.flags.add(flags);

            return result.set(Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW, srcLimit - srcIndex, 0);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }

        @Override
        public void reset()
        {
        }
    }
}
