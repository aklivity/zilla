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
package io.aklivity.zilla.runtime.engine.model;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasInit;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class ModelPipelineChainTest
{
    @Test
    public void shouldCarryUnconsumedTailOfFirstStageAcrossFragments()
    {
        BlockPipeline first = new BlockPipeline(4, 1, true);
        BlockPipeline second = new BlockPipeline(1, 0, true);
        ModelPipeline chain = first.andThen(second);

        Drive drive = new Drive(chain, 64);
        assertEquals(ModelStatus.UNDERFLOW, drive.feed("abc", INIT).status());
        assertEquals(3, drive.consumed);
        assertEquals(ModelStatus.UNDERFLOW, drive.feed("defg", 0).status());
        assertEquals(4, drive.consumed);
        assertEquals(ModelStatus.COMPLETE, drive.feed("h", FIN).status());

        assertEquals("bcdefghi", drive.output());
        assertEquals(1, first.inits);
        assertEquals(1, second.inits);
        assertEquals(1, first.fins);
        assertEquals(1, second.fins);
    }

    @Test
    public void shouldDrainSecondStageOverflowAcrossCalls()
    {
        ModelPipeline chain = new BlockPipeline(1, 1, true).andThen(new BlockPipeline(1, 0, true));

        Drive drive = new Drive(chain, 3);
        ModelStatus status = drive.feed("abcdefgh", INIT | FIN).status();
        int overflows = 0;
        while (status == ModelStatus.OVERFLOW)
        {
            overflows++;
            status = drive.feed("", FIN).status();
        }

        assertEquals(ModelStatus.COMPLETE, status);
        assertTrue(overflows > 0);
        assertEquals("bcdefghi", drive.output());
    }

    @Test
    public void shouldForwardOriginalBytesFromIdentityFirstStage()
    {
        ModelPipeline chain = new BlockPipeline(1, 0, true).andThen(new BlockPipeline(1, 1, true));

        Drive drive = new Drive(chain, 64);

        assertEquals(ModelStatus.COMPLETE, drive.feed("abcd", INIT | FIN).status());
        assertEquals("bcde", drive.output());
    }

    @Test
    public void shouldRejectWhenSecondStageRejects()
    {
        BlockPipeline first = new BlockPipeline(1, 0, true);
        BlockPipeline second = new BlockPipeline(1, 0, true).rejecting('x');
        ModelPipeline chain = first.andThen(second);

        Drive drive = new Drive(chain, 64);

        assertEquals(ModelStatus.REJECTED, drive.feed("abxd", INIT | FIN).status());
    }

    @Test
    public void shouldRejectWhenFirstStageRejects()
    {
        BlockPipeline first = new BlockPipeline(1, 0, true).rejecting('x');
        BlockPipeline second = new BlockPipeline(1, 0, true);
        ModelPipeline chain = first.andThen(second);

        Drive drive = new Drive(chain, 64);

        assertEquals(ModelStatus.REJECTED, drive.feed("abxd", INIT | FIN).status());
        assertEquals(0, second.inits);
    }

    @Test
    public void shouldResetEveryStage()
    {
        BlockPipeline first = new BlockPipeline(1, 0, true);
        BlockPipeline second = new BlockPipeline(1, 0, true);
        ModelPipeline chain = first.andThen(second).andThen(new BlockPipeline(1, 0, true));

        chain.reset();

        assertEquals(1, first.resets);
        assertEquals(1, second.resets);
    }

    @Test
    public void shouldDeliverInitOncePerStageAndFinAfterFirstStageCompletes()
    {
        BlockPipeline first = new BlockPipeline(1, 1, true);
        BlockPipeline second = new BlockPipeline(1, 0, true);
        ModelPipeline chain = first.andThen(second);

        Drive drive = new Drive(chain, 64);
        drive.feed("ab", INIT);
        drive.feed("cd", 0);
        drive.feed("ef", FIN);

        assertEquals("bcdefg", drive.output());
        assertEquals(1, first.inits);
        assertEquals(1, second.inits);
        assertEquals(1, first.fins);
        assertEquals(1, second.fins);
        assertTrue(second.finAfterAllInput);
    }

    @Test
    public void shouldFoldIdentityDeterministicAndPadding()
    {
        ModelPipeline identity = new BlockPipeline(1, 0, true);
        ModelPipeline transform = new BlockPipeline(1, 1, true);
        ModelPipeline random = new BlockPipeline(1, 0, false);

        assertTrue(identity.andThen(identity).identity());
        assertFalse(identity.andThen(transform).identity());
        assertFalse(transform.andThen(identity).identity());
        assertTrue(identity.andThen(transform).deterministic());
        assertFalse(identity.andThen(random).deterministic());
        assertFalse(random.andThen(identity).deterministic());
        assertEquals(5, new BlockPipeline(1, 0, true, 2).andThen(new BlockPipeline(1, 0, true, 3)).padding(null, 0, 0));
    }

    private static final class Drive
    {
        private final ModelPipeline pipeline;
        private final MutableDirectBufferEx dst;
        private final int window;
        private final ByteArrayOutputStream output;

        private ModelPipelineResult result;
        private int consumed;

        private Drive(
            ModelPipeline pipeline,
            int window)
        {
            this.pipeline = pipeline;
            this.dst = new UnsafeBufferEx(new byte[1024]);
            this.window = window;
            this.output = new ByteArrayOutputStream();
        }

        private ModelPipelineResult feed(
            String text,
            int flags)
        {
            final byte[] bytes = text.getBytes(UTF_8);
            final DirectBufferEx src = new UnsafeBufferEx(bytes);

            result = pipeline.transform(0L, 0L, 0L, flags, src, 0, bytes.length, dst, 0, window);
            consumed = result.consumed();
            for (int index = 0; index < result.produced(); index++)
            {
                output.write(dst.getByte(index));
            }

            return result;
        }

        private String output()
        {
            return new String(output.toByteArray(), UTF_8);
        }
    }

    private static final class BlockPipeline implements ModelPipeline
    {
        private final int block;
        private final int delta;
        private final boolean deterministic;
        private final int padding;
        private final ModelPipelineResult result;

        private int inits;
        private int fins;
        private int resets;
        private int rejected = -1;
        private boolean finAfterAllInput;
        private int received;

        private BlockPipeline(
            int block,
            int delta,
            boolean deterministic)
        {
            this(block, delta, deterministic, 0);
        }

        private BlockPipeline(
            int block,
            int delta,
            boolean deterministic,
            int padding)
        {
            this.block = block;
            this.delta = delta;
            this.deterministic = deterministic;
            this.padding = padding;
            this.result = new ModelPipelineResult();
        }

        private BlockPipeline rejecting(
            char value)
        {
            this.rejected = value;
            return this;
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
            inits += hasInit(flags) ? 1 : 0;
            fins += hasFin(flags) ? 1 : 0;

            final int available = srcLimit - srcIndex;
            final int room = dstLimit - dstIndex;
            final int blocks = Math.min(available, room) / block * block;

            ModelStatus status;
            int produced = 0;
            for (int index = 0; index < blocks; index++)
            {
                final byte value = src.getByte(srcIndex + index);
                if (value == rejected)
                {
                    status = ModelStatus.REJECTED;
                    return result.set(status, 0, 0);
                }
                dst.putByte(dstIndex + index, (byte) (value + delta));
                produced++;
            }

            received += blocks;
            if (blocks < available && blocks == room / block * block)
            {
                status = ModelStatus.OVERFLOW;
            }
            else if (hasFin(flags) && blocks == available)
            {
                finAfterAllInput = true;
                status = ModelStatus.COMPLETE;
            }
            else
            {
                status = ModelStatus.UNDERFLOW;
            }

            return result.set(status, blocks, produced);
        }

        @Override
        public boolean identity()
        {
            return delta == 0;
        }

        @Override
        public boolean deterministic()
        {
            return deterministic;
        }

        @Override
        public int padding(
            DirectBufferEx data,
            int index,
            int length)
        {
            return padding;
        }

        @Override
        public void reset()
        {
            resets++;
        }
    }
}
