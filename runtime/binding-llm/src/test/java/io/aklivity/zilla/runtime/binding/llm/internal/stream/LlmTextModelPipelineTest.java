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

import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

public class LlmTextModelPipelineTest
{
    @Test
    public void shouldTransformTextOnly()
    {
        assertTransformed("{\"text\":\"hello\"}", "{\"text\":\"HELLO\"}");
    }

    @Test
    public void shouldRelayLeadingMemberBeforeText()
    {
        assertTransformed("{\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"text\":\"my secret\"}",
            "{\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"text\":\"MY SECRET\"}");
    }

    @Test
    public void shouldRelayTrailingMembersAfterText()
    {
        assertTransformed("{\"text\":\"my secret\",\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"x\":[1,2]}",
            "{\"text\":\"MY SECRET\",\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"x\":[1,2]}");
    }

    @Test
    public void shouldRelayMembersAroundTextVerbatim()
    {
        assertTransformed("{ \"a\" : 1 , \"text\" :  \"hi\" , \"b\" : 2 }",
            "{ \"a\" : 1 , \"text\" :  \"HI\" , \"b\" : 2 }");
    }

    @Test
    public void shouldEscapeTransformedText()
    {
        assertTransformed("{\"text\":\"say \\\"hi\\\"\"}", "{\"text\":\"SAY \\\"HI\\\"\"}");
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
                assertThat("dst " + dstSize + " chunk " + chunk, run(bytes, chunk, dstSize), equalTo(expected));
            }
        }
    }

    private String run(
        byte[] bytes,
        int chunk,
        int dstSize)
    {
        final LlmTextModelPipeline pipeline = new LlmTextModelPipeline(new UppercasePipeline(), new TestBufferPool(), 1L,
            new UnsafeBufferEx(new byte[dstSize]));
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[dstSize]);
        final StringBuilder output = new StringBuilder();

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
            status = result.status();
        }
        while (status == ModelStatus.OVERFLOW);

        final int length = pipeline.close(dst, 0);
        output.append(dst.getStringWithoutLengthUtf8(0, length));

        return output.toString();
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

    private static final class TestBufferPool implements BufferPool
    {
        private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        @Override
        public int slotCapacity()
        {
            return buffer.capacity();
        }

        @Override
        public int acquire(
            long streamId)
        {
            return 0;
        }

        @Override
        public MutableDirectBufferEx buffer(
            int slot)
        {
            return buffer;
        }

        @Override
        public ByteBuffer byteBuffer(
            int slot)
        {
            return buffer.byteBuffer();
        }

        @Override
        public MutableDirectBufferEx buffer(
            int slot,
            int offset)
        {
            return buffer;
        }

        @Override
        public void release(
            int slot)
        {
        }

        @Override
        public BufferPool duplicate()
        {
            return this;
        }

        @Override
        public int acquiredSlots()
        {
            return 1;
        }
    }
}
