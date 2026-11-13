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

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public class LlmContentAccumulatorTest
{
    private static final int CAPACITY = 64;
    private static final int HEADER = 5;

    private final TestBufferPool pool = new TestBufferPool(CAPACITY, 1);
    private final LlmContentAccumulator accumulator = new LlmContentAccumulator(pool, 1L);

    @Test
    public void shouldStoreValuesWithinBound()
    {
        final int first = accumulator.open();
        accumulator.append(buffer("abc"), 0, 3);
        accumulator.append(buffer("def"), 0, 3);
        accumulator.close();

        final int second = accumulator.open();
        accumulator.append(buffer("xyz"), 1, 2);
        accumulator.close();

        assertThat(accumulator.count(), is(2));
        assertThat(accumulator.bound(), is(CAPACITY));
        assertThat(accumulator.length(first), is(6));
        assertThat(accumulator.length(second), is(2));
        assertThat(accumulator.offset(first), is(HEADER));
        assertThat(accumulator.offset(second), is(HEADER + 6 + HEADER));
        assertThat(accumulator.truncated(first), is(false));
        assertThat(text(accumulator, first), equalTo("abcdef"));
        assertThat(text(accumulator, second), equalTo("yz"));
    }

    @Test
    public void shouldTruncateValueBeyondBoundAndKeepLaterValues()
    {
        final LlmContentAccumulator bounded = new LlmContentAccumulator(new TestBufferPool(28, 1), 1L);

        final int first = bounded.open();
        bounded.append(buffer("0123456789"), 0, 10);
        bounded.close();

        final int second = bounded.open();
        bounded.append(buffer("abcdefghij"), 0, 10);
        bounded.append(buffer("k"), 0, 1);
        bounded.close();

        final int third = bounded.open();
        bounded.append(buffer("xyz"), 0, 3);
        bounded.close();

        assertThat(bounded.truncated(second), is(true));
        assertThat(bounded.length(second), is(0));
        assertThat(text(bounded, first), equalTo("0123456789"));
        assertThat(text(bounded, third), equalTo("xyz"));
    }

    @Test
    public void shouldIgnoreAppendWithoutOpenValue()
    {
        accumulator.append(buffer("abc"), 0, 3);

        assertThat(accumulator.count(), is(0));
        assertThat(accumulator.buffer(), nullValue());
    }

    @Test
    public void shouldLocateManyValuesInAnyOrder()
    {
        final LlmContentAccumulator large = new LlmContentAccumulator(new TestBufferPool(512, 1), 1L);

        for (int index = 0; index < 50; index++)
        {
            large.open();
            large.append(buffer("a"), 0, index % 2);
            large.close();
        }

        assertThat(large.count(), is(50));
        assertThat(large.length(49), is(1));
        assertThat(large.length(48), is(0));
        assertThat(large.length(7), is(1));
        assertThat(large.length(30), is(0));
        assertThat(large.offset(31), is(large.offset(30) + 0 + HEADER));
        assertThat(large.length(49), is(1));
    }

    @Test
    public void shouldOpenNothingWhenNoSlotAvailable()
    {
        final LlmContentAccumulator exhausted = new LlmContentAccumulator(new TestBufferPool(CAPACITY, 0), 1L);

        final int value = exhausted.open();
        exhausted.append(buffer("abc"), 0, 3);

        assertThat(value, is(LlmContentAccumulator.NONE));
        assertThat(exhausted.count(), is(0));
    }

    @Test
    public void shouldOpenNothingWhenHeaderDoesNotFit()
    {
        final LlmContentAccumulator bounded = new LlmContentAccumulator(new TestBufferPool(8, 1), 1L);

        final int first = bounded.open();
        bounded.close();
        final int second = bounded.open();

        assertThat(first, is(0));
        assertThat(second, is(LlmContentAccumulator.NONE));
        assertThat(bounded.count(), is(1));
    }

    @Test
    public void shouldKeepKindOfValue()
    {
        final int plain = accumulator.open();
        accumulator.close();
        final int typed = accumulator.open(3);
        accumulator.append(buffer("abc"), 0, 3);
        accumulator.close();

        assertThat(accumulator.kind(plain), is(0));
        assertThat(accumulator.kind(typed), is(3));
        assertThat(accumulator.length(typed), is(3));
    }

    @Test
    public void shouldPatchAndReadIntWithinValue()
    {
        final int value = accumulator.open();
        accumulator.append(buffer("12345678"), 0, 8);
        accumulator.close();

        accumulator.putInt(value, 4, 0x01020304);

        assertThat(accumulator.getInt(value, 4), is(0x01020304));
        assertThat(accumulator.length(value), is(8));
    }

    @Test
    public void shouldAppendIntAndText()
    {
        final int value = accumulator.open();
        accumulator.appendInt(7);
        accumulator.appendText("héllo");
        accumulator.appendText(null);
        accumulator.close();

        final MutableDirectBufferEx buffer = accumulator.buffer();
        final int base = accumulator.offset(value);

        assertThat(accumulator.length(value), is(4 + 4 + 6 + 4));
        assertThat(buffer.getInt(base), is(7));
        assertThat(buffer.getInt(base + 4), is(6));
        assertThat(buffer.getStringWithoutLengthUtf8(base + 8, 6), equalTo("héllo"));
        assertThat(buffer.getInt(base + 14), is(-1));
    }

    @Test
    public void shouldNotPatchBeyondTruncatedValue()
    {
        final LlmContentAccumulator bounded = new LlmContentAccumulator(new TestBufferPool(14, 1), 1L);

        final int value = bounded.open();
        bounded.appendText("0123456789");
        bounded.close();
        final int next = bounded.open();
        bounded.appendInt(5);
        bounded.close();

        bounded.putInt(value, 0, 99);

        assertThat(bounded.truncated(value), is(true));
        assertThat(bounded.getInt(value, 0), is(0));
        assertThat(bounded.getInt(next, 0), is(5));
    }

    @Test
    public void shouldTolerateNoValue()
    {
        assertThat(accumulator.length(LlmContentAccumulator.NONE), is(0));
        assertThat(accumulator.offset(LlmContentAccumulator.NONE), is(0));
        assertThat(accumulator.truncated(LlmContentAccumulator.NONE), is(true));
        assertThat(accumulator.kind(LlmContentAccumulator.NONE), is(0));
    }

    @Test
    public void shouldReleaseSlot()
    {
        accumulator.open();
        accumulator.append(buffer("abc"), 0, 3);
        accumulator.release();

        assertThat(pool.acquiredSlots(), is(0));
        assertThat(accumulator.count(), is(0));

        final int value = accumulator.open();
        accumulator.append(buffer("de"), 0, 2);

        assertThat(text(accumulator, value), equalTo("de"));
    }

    private static String text(
        LlmContentAccumulator target,
        int value)
    {
        final byte[] bytes = new byte[target.length(value)];

        target.buffer().getBytes(target.offset(value), bytes);

        return new String(bytes, UTF_8);
    }

    private static DirectBufferEx buffer(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }

    private static final class TestBufferPool implements BufferPool
    {
        private final int capacity;
        private final int slots;
        private final List<MutableDirectBufferEx> buffers;
        private final boolean[] acquired;

        private TestBufferPool(
            int capacity,
            int slots)
        {
            this.capacity = capacity;
            this.slots = slots;
            this.buffers = new ArrayList<>();
            this.acquired = new boolean[slots];

            for (int index = 0; index < slots; index++)
            {
                buffers.add(new UnsafeBufferEx(new byte[capacity]));
            }
        }

        @Override
        public int slotCapacity()
        {
            return capacity;
        }

        @Override
        public int acquire(
            long streamId)
        {
            int slot = NO_SLOT;

            for (int index = 0; index < slots && slot == NO_SLOT; index++)
            {
                if (!acquired[index])
                {
                    acquired[index] = true;
                    slot = index;
                }
            }

            return slot;
        }

        @Override
        public MutableDirectBufferEx buffer(
            int slot)
        {
            return buffers.get(slot);
        }

        @Override
        public ByteBuffer byteBuffer(
            int slot)
        {
            return null;
        }

        @Override
        public MutableDirectBufferEx buffer(
            int slot,
            int offset)
        {
            return buffers.get(slot);
        }

        @Override
        public void release(
            int slot)
        {
            acquired[slot] = false;
        }

        @Override
        public BufferPool duplicate()
        {
            return this;
        }

        @Override
        public int acquiredSlots()
        {
            int count = 0;

            for (int index = 0; index < slots; index++)
            {
                count += acquired[index] ? 1 : 0;
            }

            return count;
        }
    }
}
