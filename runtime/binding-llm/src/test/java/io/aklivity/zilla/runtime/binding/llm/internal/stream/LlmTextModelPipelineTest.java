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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

public class LlmTextModelPipelineTest
{
    private static final String OBJECT = "{\"text\":\"héllo \\\"x\\\" \\n\\u0001\"}";
    private static final int[] FRAGMENTS = {1, 2, 3, 5, 8, 1000};
    private static final int[] CAPACITIES = {32, 64, 4096};

    @Test
    public void shouldTransformDecodedTextAndEmbedAsObject()
    {
        for (int fragment : FRAGMENTS)
        {
            for (int capacity : CAPACITIES)
            {
                final ByteArrayOutputStream output = new ByteArrayOutputStream();
                final ModelStatus status = drive(new LlmTextModelPipeline(new Upper()), OBJECT, fragment, capacity, output);

                assertThat("fragment " + fragment + " capacity " + capacity, status, is(ModelStatus.COMPLETE));
                assertThat("fragment " + fragment + " capacity " + capacity, output.toString(UTF_8),
                    equalTo("{\"text\":\"HéLLO \\\"X\\\" \\n\\u0001\"}"));
            }
        }
    }

    @Test
    public void shouldNotEmitFramingBeforeModelProducesOutput()
    {
        final LlmTextModelPipeline pipeline = new LlmTextModelPipeline(new Holding());
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[64]);
        final byte[] bytes = "{\"text\":\"ab".getBytes(UTF_8);
        final ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, Flags.INIT, new UnsafeBufferEx(bytes), 0,
            bytes.length, dst, 0, dst.capacity());

        assertThat(result.status(), is(ModelStatus.UNDERFLOW));
        assertThat(result.consumed(), is(bytes.length));
        assertThat(result.produced(), is(0));
    }

    @Test
    public void shouldValidateDecodedTextWithoutEmbedding()
    {
        for (int fragment : FRAGMENTS)
        {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final ModelStatus status = drive(new LlmTextModelPipeline(new Reject()), "{\"text\":\"fine \\\"text\\\"\"}",
                fragment, 4096, output);

            assertThat("fragment " + fragment, status, is(ModelStatus.COMPLETE));
            assertThat("fragment " + fragment, output.size(), is(0));
        }
    }

    @Test
    public void shouldRejectDecodedText()
    {
        for (int fragment : FRAGMENTS)
        {
            final ModelStatus status = drive(new LlmTextModelPipeline(new Reject()), "{\"text\":\"b\\u0061d\"}", fragment, 4096,
                new ByteArrayOutputStream());

            assertThat("fragment " + fragment, status, is(ModelStatus.REJECTED));
        }
    }

    @Test
    public void shouldRejectInvalidObject()
    {
        final ModelStatus status = drive(new LlmTextModelPipeline(new Upper()), "[1]", 1000, 4096,
            new ByteArrayOutputStream());

        assertThat(status, is(ModelStatus.REJECTED));
    }

    @Test
    public void shouldResetBetweenSections()
    {
        final LlmTextModelPipeline pipeline = new LlmTextModelPipeline(new Upper());

        for (int section = 0; section < 2; section++)
        {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            assertThat(drive(pipeline, "{\"text\":\"ab\"}", 3, 64, output), is(ModelStatus.COMPLETE));
            assertThat(output.toString(UTF_8), equalTo("{\"text\":\"AB\"}"));

            pipeline.reset();
        }
    }

    private static ModelStatus drive(
        LlmTextModelPipeline pipeline,
        String json,
        int fragment,
        int capacity,
        ByteArrayOutputStream output)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx src = new UnsafeBufferEx(bytes);
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[capacity]);

        ModelStatus status = ModelStatus.UNDERFLOW;

        for (int offset = 0; offset < bytes.length && status != ModelStatus.REJECTED; offset += fragment)
        {
            final int limit = Math.min(offset + fragment, bytes.length);
            final boolean last = limit == bytes.length;

            int flags = (offset == 0 ? Flags.INIT : 0) | (last ? Flags.FIN : 0);
            int position = offset;

            do
            {
                final ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, flags, src, position, limit, dst, 0,
                    capacity);

                status = result.status();
                position += result.consumed();
                flags &= ~Flags.INIT;

                final byte[] produced = new byte[result.produced()];
                dst.getBytes(0, produced);
                output.write(produced, 0, produced.length);

                assertThat("progress", status != ModelStatus.OVERFLOW || result.produced() > 0 || result.consumed() > 0,
                    is(true));
            }
            while (status == ModelStatus.OVERFLOW);
        }

        return status;
    }

    private abstract static class Stage implements ModelPipeline
    {
        protected final ModelPipelineResult result = new ModelPipelineResult();

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

    private static final class Upper extends Stage
    {
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
            final int length = Math.min(srcLimit - srcIndex, dstLimit - dstIndex);

            for (int index = 0; index < length; index++)
            {
                final byte value = src.getByte(srcIndex + index);

                dst.putByte(dstIndex + index, value >= 'a' && value <= 'z' ? (byte) (value - 'a' + 'A') : value);
            }

            final boolean drained = length == srcLimit - srcIndex;
            final ModelStatus status = !drained ? ModelStatus.OVERFLOW
                : Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;

            return result.set(status, length, length);
        }

        @Override
        public boolean identity()
        {
            return false;
        }
    }

    private static final class Holding extends Stage
    {
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
            return result.set(ModelStatus.UNDERFLOW, srcLimit - srcIndex, 0);
        }

        @Override
        public boolean identity()
        {
            return false;
        }
    }

    private static final class Reject extends Stage
    {
        private final StringBuilder text = new StringBuilder();

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
            text.append(src.getStringWithoutLengthUtf8(srcIndex, srcLimit - srcIndex));

            final ModelStatus status = !Flags.hasFin(flags) ? ModelStatus.UNDERFLOW
                : "bad".contentEquals(text) ? ModelStatus.REJECTED : ModelStatus.COMPLETE;

            return result.set(status, srcLimit - srcIndex, 0);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public void reset()
        {
            text.setLength(0);
        }
    }
}
