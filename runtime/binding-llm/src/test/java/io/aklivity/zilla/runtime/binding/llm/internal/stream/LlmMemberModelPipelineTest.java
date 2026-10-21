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

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

public class LlmMemberModelPipelineTest
{
    @Test
    public void shouldTransformMemberValueOnly()
    {
        assertTransformed("{\"arguments\":{\"city\":\"paris\"}}", "{\"arguments\":{\"CITY\":\"PARIS\"}}");
    }

    @Test
    public void shouldRelayLeadingMemberBeforeValue()
    {
        assertTransformed("{\"anthropic:note\":\"secret\",\"arguments\":{\"a\":\"b\"}}",
            "{\"anthropic:note\":\"secret\",\"arguments\":{\"A\":\"B\"}}");
    }

    @Test
    public void shouldRelayTrailingMembersAfterValue()
    {
        assertTransformed("{\"arguments\":{\"a\":\"b\"},\"anthropic:cache_control\":{\"type\":\"ephemeral\"}}",
            "{\"arguments\":{\"A\":\"B\"},\"anthropic:cache_control\":{\"type\":\"ephemeral\"}}");
    }

    @Test
    public void shouldRelayMembersAroundValueVerbatim()
    {
        assertTransformed("{ \"a\" : 1 , \"arguments\" :  {\"x\":1} , \"b\" : \"c\" }",
            "{ \"a\" : 1 , \"arguments\" :  {\"X\":1} , \"b\" : \"c\" }");
    }

    @Test
    public void shouldTransformStringValue()
    {
        assertTransformed("{\"arguments\":\"{\\\"a\\\":1}\"}", "{\"arguments\":\"{\\\"A\\\":1}\"}");
    }

    @Test
    public void shouldRelayObjectWithoutMemberUnchanged()
    {
        assertTransformed("{\"anthropic:note\":\"secret\"}", "{\"anthropic:note\":\"secret\"}");
    }

    @Test
    public void shouldCountValueBytesOnly()
    {
        assertValues("{\"anthropic:note\":\"x\",\"arguments\":{\"a\":\"b\"},\"z\":1}", "{\"a\":\"b\"}".length());
    }

    @Test
    public void shouldRejectMemberValueOnly()
    {
        assertRejected("{\"arguments\":{\"city\":\"bad\"}}", true);
        assertRejected("{\"anthropic:note\":{\"city\":\"bad\"},\"arguments\":{\"city\":\"ok\"}}", false);
        assertRejected("{\"arguments\":{\"city\":\"ok\"},\"anthropic:note\":{\"city\":\"bad\"}}", false);
    }

    private void assertRejected(
        String json,
        boolean rejected)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        for (int chunk = 1; chunk <= bytes.length; chunk++)
        {
            final LlmMemberModelPipeline pipeline = new LlmMemberModelPipeline(new RejectPipeline("{\"city\":\"bad\"}"),
                "arguments");
            final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[256]);

            ModelStatus status = ModelStatus.UNDERFLOW;
            boolean init = true;
            for (int from = 0; from < bytes.length && status != ModelStatus.REJECTED; from += chunk)
            {
                final int limit = Math.min(from + chunk, bytes.length);
                final UnsafeBufferEx src = new UnsafeBufferEx(bytes, 0, limit);

                int position = from;
                do
                {
                    final ModelPipelineResult result = pipeline.accept(0L, 0L, 0L, init, limit == bytes.length, src, position,
                        limit, dst, 0, 256);

                    position += result.consumed();
                    init = false;
                    status = result.status();
                }
                while (status == ModelStatus.OVERFLOW);
            }

            if (status != ModelStatus.REJECTED)
            {
                status = pipeline.finish(0L, 0L, 0L, dst, 0, 256).status();
            }

            assertThat("chunk " + chunk, status == ModelStatus.REJECTED, equalTo(rejected));
        }
    }

    private void assertTransformed(
        String json,
        String expected)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        for (int dstSize = 16; dstSize <= 256; dstSize *= 4)
        {
            for (int chunk = 1; chunk <= bytes.length; chunk++)
            {
                assertThat("dst " + dstSize + " chunk " + chunk, run(bytes, chunk, dstSize).output, equalTo(expected));
            }
        }
    }

    private void assertValues(
        String json,
        int expected)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        for (int dstSize = 16; dstSize <= 256; dstSize *= 4)
        {
            for (int chunk = 1; chunk <= bytes.length; chunk++)
            {
                assertThat("dst " + dstSize + " chunk " + chunk, run(bytes, chunk, dstSize).values, equalTo(expected));
            }
        }
    }

    private Run run(
        byte[] bytes,
        int chunk,
        int dstSize)
    {
        final LlmMemberModelPipeline pipeline = new LlmMemberModelPipeline(new UppercasePipeline(), "arguments");
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[dstSize]);
        final StringBuilder output = new StringBuilder();
        int values = 0;

        boolean init = true;
        for (int from = 0; from < bytes.length; from += chunk)
        {
            final int limit = Math.min(from + chunk, bytes.length);
            final UnsafeBufferEx src = new UnsafeBufferEx(bytes, 0, limit);

            int position = from;
            ModelStatus status;
            do
            {
                final ModelPipelineResult result = pipeline.accept(0L, 0L, 0L, init, limit == bytes.length, src, position,
                    limit, dst, 0, dstSize);

                output.append(dst.getStringWithoutLengthUtf8(0, result.produced()));
                values += pipeline.values();
                position += result.consumed();
                init = false;
                status = result.status();
            }
            while (status == ModelStatus.OVERFLOW);
        }

        ModelStatus status;
        do
        {
            final ModelPipelineResult result = pipeline.finish(0L, 0L, 0L, dst, 0, dstSize);

            output.append(dst.getStringWithoutLengthUtf8(0, result.produced()));
            values += pipeline.values();
            status = result.status();
        }
        while (status == ModelStatus.OVERFLOW);

        return new Run(output.toString(), values);
    }

    private record Run(
        String output,
        int values)
    {
    }

    private static final class UppercasePipeline implements ModelPipeline
    {
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
            final int length = Math.min(srcLimit - srcIndex, dstLimit - dstIndex);

            for (int index = 0; index < length; index++)
            {
                dst.putByte(dstIndex + index, (byte) Character.toUpperCase((char) src.getByte(srcIndex + index)));
            }

            final ModelStatus status = length < srcLimit - srcIndex ? ModelStatus.OVERFLOW :
                Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;

            return result.set(status, length, length);
        }

        @Override
        public boolean identity()
        {
            return false;
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

    private static final class RejectPipeline implements ModelPipeline
    {
        private final ModelPipelineResult result = new ModelPipelineResult();
        private final String pattern;
        private final StringBuilder buffer = new StringBuilder();

        RejectPipeline(
            String pattern)
        {
            this.pattern = pattern;
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
            buffer.append(src.getStringWithoutLengthUtf8(srcIndex, srcLimit - srcIndex));

            final boolean rejected = Flags.hasFin(flags) && buffer.toString().equals(pattern);

            final ModelStatus status = rejected ? ModelStatus.REJECTED :
                Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;

            return result.set(status, srcLimit - srcIndex, 0);
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
            buffer.setLength(0);
        }
    }
}
