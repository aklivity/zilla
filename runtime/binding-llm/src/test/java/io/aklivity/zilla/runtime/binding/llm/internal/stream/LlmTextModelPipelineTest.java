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
import static org.hamcrest.Matchers.lessThan;

import java.io.ByteArrayOutputStream;
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

public class LlmTextModelPipelineTest
{
    private static final String OBJECT = "{\"text\":\"héllo \\\"x\\\" \\n\\u0001\"}";
    private static final int[] FRAGMENTS = {1, 2, 3, 5, 8, 1000};
    private static final int[] CAPACITIES = {32, 64, 4096};
    private static final int RESIDUAL_MAX = 1024;

    @Test
    public void shouldTransformDecodedTextAndEmbedAsObject()
    {
        for (int fragment : FRAGMENTS)
        {
            for (int capacity : CAPACITIES)
            {
                final Run run = run(new LlmTextModelPipeline(new Upper(), 0, RESIDUAL_MAX), List.of(OBJECT), fragment, capacity);

                assertThat("fragment " + fragment + " capacity " + capacity, run.status, is(ModelStatus.COMPLETE));
                assertThat("fragment " + fragment + " capacity " + capacity, run.sections,
                    equalTo(List.of("{\"text\":\"HéLLO \\\"X\\\" \\n\\u0001\"}")));
            }
        }
    }

    @Test
    public void shouldKeepMembersOtherThanTextAfterTransformedText()
    {
        for (int fragment : FRAGMENTS)
        {
            final Run before = run(new LlmTextModelPipeline(new Upper(), 0, RESIDUAL_MAX),
                List.of("{\"sig\":{\"k\":[1,\"v\"]},\"text\":\"ab\"}"), fragment, 64);
            final Run after = run(new LlmTextModelPipeline(new Upper(), 0, RESIDUAL_MAX),
                List.of("{\"text\":\"ab\",\"sig\":{\"k\":[1,\"v\"]},\"n\":2}"), fragment, 64);

            assertThat("fragment " + fragment, before.sections,
                equalTo(List.of("{\"text\":\"AB\",\"sig\":{\"k\":[1,\"v\"]}}")));
            assertThat("fragment " + fragment, after.sections,
                equalTo(List.of("{\"text\":\"AB\",\"sig\":{\"k\":[1,\"v\"]},\"n\":2}")));
        }
    }

    @Test
    public void shouldKeepMembersOfEachSectionOfRun()
    {
        final Run run = run(new LlmTextModelPipeline(new Upper(), 0, RESIDUAL_MAX),
            List.of("{\"text\":\"ab\",\"a\":1}", "{\"b\":2,\"text\":\"cd\"}"), 3, 64);

        assertThat(run.sections, equalTo(List.of("{\"text\":\"AB\",\"a\":1}", "{\"text\":\"CD\",\"b\":2}")));
    }

    @Test
    public void shouldRejectMembersLargerThanResidualMax()
    {
        final Run run = run(new LlmTextModelPipeline(new Upper(), 0, 8),
            List.of("{\"text\":\"ab\",\"member\":\"0123456789\"}"), 1000, 64);

        assertThat(run.statuses.get(0), is(ModelStatus.REJECTED));
    }

    @Test
    public void shouldPresentSectionsOfRunAsOneValue()
    {
        final List<String> input = List.of("{\"text\":\"ab\"}", "{\"text\":\"cd\"}", "{\"text\":\"ef\"}");

        for (int fragment : FRAGMENTS)
        {
            final Run run = run(new LlmTextModelPipeline(new Upper(), 0, RESIDUAL_MAX), input, fragment, 64);

            assertThat("fragment " + fragment, run.sections,
                equalTo(List.of("{\"text\":\"AB\"}", "{\"text\":\"CD\"}", "{\"text\":\"EF\"}")));
        }
    }

    @Test
    public void shouldHoldOverlapBackUntilMoreTextOrRunEnd()
    {
        final List<String> input = List.of("{\"text\":\"abcd\"}", "{\"text\":\"efgh\"}", "{\"text\":\"ij\"}");

        for (int fragment : FRAGMENTS)
        {
            final Run run = run(new LlmTextModelPipeline(new Upper(), 3, RESIDUAL_MAX), input, fragment, 64);

            assertThat("fragment " + fragment, run.sections,
                equalTo(List.of("{\"text\":\"A\"}", "{\"text\":\"BCDE\"}", "{\"text\":\"FGHIJ\"}")));
        }
    }

    @Test
    public void shouldCloseSectionHoldingNoOutput()
    {
        final Run run = run(new LlmTextModelPipeline(new Upper(), 100, RESIDUAL_MAX),
            List.of("{\"text\":\"ab\"}", "{\"text\":\"cd\"}"), 3, 64);

        assertThat(run.sections, equalTo(List.of("{\"text\":\"\"}", "{\"text\":\"ABCD\"}")));
    }

    @Test
    public void shouldRetainUnconsumedDecodedTail()
    {
        for (int fragment : FRAGMENTS)
        {
            final Run run = run(new LlmTextModelPipeline(new Pairs(), 0, RESIDUAL_MAX),
                List.of("{\"text\":\"abcdefg\"}"), fragment, 64);

            assertThat("fragment " + fragment, run.status, is(ModelStatus.COMPLETE));
            assertThat("fragment " + fragment, String.join("", run.sections), equalTo("{\"text\":\"ABCDEFG\"}"));
        }
    }

    @Test
    public void shouldNotEmitFramingBeforeChainProducesOutput()
    {
        final LlmTextModelPipeline pipeline = new LlmTextModelPipeline(new Holding(), 0, RESIDUAL_MAX);
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[64]);
        final byte[] bytes = "{\"text\":\"ab".getBytes(UTF_8);
        final ModelPipelineResult result = pipeline.accept(0L, 0L, 0L, true, false, new UnsafeBufferEx(bytes), 0,
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
            final Run run = run(new LlmTextModelPipeline(new Reject(), 0, RESIDUAL_MAX),
                List.of("{\"text\":\"fine \\\"text\\\"\"}"),
                fragment, 4096);

            assertThat("fragment " + fragment, run.status, is(ModelStatus.COMPLETE));
            assertThat("fragment " + fragment, run.sections.size(), is(0));
        }
    }

    @Test
    public void shouldRejectOnlyWhenRunEnds()
    {
        for (int fragment : FRAGMENTS)
        {
            final LlmTextModelPipeline pipeline = new LlmTextModelPipeline(new Reject(), 0, RESIDUAL_MAX);
            final Run run = run(pipeline, List.of("{\"text\":\"b\"}", "{\"text\":\"a\"}", "{\"text\":\"d\"}"), fragment, 4096);

            assertThat("fragment " + fragment, run.statuses.subList(0, run.statuses.size() - 1)
                .contains(ModelStatus.REJECTED), is(false));
            assertThat("fragment " + fragment, run.status, is(ModelStatus.REJECTED));
        }
    }

    @Test
    public void shouldRejectInvalidObject()
    {
        final Run run = run(new LlmTextModelPipeline(new Upper(), 0, RESIDUAL_MAX), List.of("[1]"), 1000, 4096);

        assertThat(run.statuses.get(0), is(ModelStatus.REJECTED));
    }

    @Test
    public void shouldResetBetweenRuns()
    {
        final LlmTextModelPipeline pipeline = new LlmTextModelPipeline(new Upper(), 0, RESIDUAL_MAX);

        for (int index = 0; index < 2; index++)
        {
            assertThat(run(pipeline, List.of("{\"text\":\"ab\"}"), 3, 64).sections, equalTo(List.of("{\"text\":\"AB\"}")));

            pipeline.reset();
        }
    }

    @Test
    public void shouldHoldLessThanWholeValueWhenOverlapIsSmall()
    {
        final Run run = run(new LlmTextModelPipeline(new Upper(), 2, RESIDUAL_MAX), List.of("{\"text\":\"abcdefgh\"}"), 1000, 64);

        assertThat(run.sections.size(), is(1));
        assertThat(run.sections.get(0).length(), lessThan("{\"text\":\"abcdefgh\"}".length() + 1));
    }

    private static Run run(
        LlmTextModelPipeline pipeline,
        List<String> sections,
        int fragment,
        int capacity)
    {
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[capacity]);
        final Run run = new Run();

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (int section = 0; section < sections.size() && run.status != ModelStatus.REJECTED; section++)
        {
            if (section > 0)
            {
                run.sections.add(close(pipeline, dst, output));
                output = new ByteArrayOutputStream();
            }

            final byte[] bytes = sections.get(section).getBytes(UTF_8);
            final UnsafeBufferEx src = new UnsafeBufferEx(bytes);

            for (int offset = 0; offset < bytes.length && run.status != ModelStatus.REJECTED; offset += fragment)
            {
                final int limit = Math.min(offset + fragment, bytes.length);

                int position = offset;
                boolean init = offset == 0;
                ModelStatus status;

                do
                {
                    final ModelPipelineResult result = pipeline.accept(0L, 0L, 0L, init, limit == bytes.length, src,
                        position, limit, dst, 0, capacity);

                    status = result.status();
                    position += result.consumed();
                    init = false;
                    collect(output, dst, result.produced());
                    run.statuses.add(status);

                    assertThat("progress", status != ModelStatus.OVERFLOW || result.produced() > 0 || result.consumed() > 0,
                        is(true));
                }
                while (status == ModelStatus.OVERFLOW);

                run.status = status;
            }
        }

        if (run.status != ModelStatus.REJECTED)
        {
            ModelStatus status;

            do
            {
                final ModelPipelineResult result = pipeline.finish(0L, 0L, 0L, dst, 0, capacity);

                status = result.status();
                collect(output, dst, result.produced());
                run.statuses.add(status);
            }
            while (status == ModelStatus.OVERFLOW);

            run.status = status;

            if (!pipeline.identity())
            {
                run.sections.add(close(pipeline, dst, output));
            }
        }

        return run;
    }

    private static String close(
        LlmTextModelPipeline pipeline,
        UnsafeBufferEx dst,
        ByteArrayOutputStream output)
    {
        collect(output, dst, pipeline.close(dst, 0));

        return output.toString(UTF_8);
    }

    private static void collect(
        ByteArrayOutputStream output,
        UnsafeBufferEx dst,
        int length)
    {
        final byte[] produced = new byte[length];
        dst.getBytes(0, produced);
        output.write(produced, 0, length);
    }

    private static final class Run
    {
        private final List<String> sections = new ArrayList<>();
        private final List<ModelStatus> statuses = new ArrayList<>();

        private ModelStatus status = ModelStatus.UNDERFLOW;
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

    private static final class Pairs extends Stage
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
            final int available = srcLimit - srcIndex;
            final int length = Flags.hasFin(flags) ? available : available & ~1;

            for (int index = 0; index < length; index++)
            {
                dst.putByte(dstIndex + index, (byte) Character.toUpperCase(src.getByte(srcIndex + index)));
            }

            return result.set(Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW, length, length);
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
