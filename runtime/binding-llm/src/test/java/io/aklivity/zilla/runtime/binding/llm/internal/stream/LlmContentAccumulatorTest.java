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
    private static final int CAPACITY = 16;

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
        assertThat(accumulator.truncated(first), is(false));
        assertThat(text(first), equalTo("abcdef"));
        assertThat(text(second), equalTo("yz"));
    }

    @Test
    public void shouldTruncateValueBeyondBoundAndKeepLaterValues()
    {
        final int first = accumulator.open();
        accumulator.append(buffer("0123456789"), 0, 10);
        accumulator.close();

        final int second = accumulator.open();
        accumulator.append(buffer("abcdefghij"), 0, 10);
        accumulator.append(buffer("k"), 0, 1);
        accumulator.close();

        final int third = accumulator.open();
        accumulator.append(buffer("xyz"), 0, 3);
        accumulator.close();

        assertThat(accumulator.truncated(second), is(true));
        assertThat(accumulator.length(second), is(0));
        assertThat(text(first), equalTo("0123456789"));
        assertThat(text(third), equalTo("xyz"));
    }

    @Test
    public void shouldIgnoreAppendWithoutOpenValue()
    {
        accumulator.append(buffer("abc"), 0, 3);

        assertThat(accumulator.count(), is(0));
        assertThat(accumulator.buffer(), nullValue());
    }

    @Test
    public void shouldGrowIndexes()
    {
        for (int index = 0; index < 20; index++)
        {
            accumulator.open();
            accumulator.append(buffer("a"), 0, index % 2);
            accumulator.close();
        }

        assertThat(accumulator.count(), is(20));
        assertThat(accumulator.length(19), is(1));
        assertThat(accumulator.length(18), is(0));
    }

    @Test
    public void shouldTruncateWhenNoSlotAvailable()
    {
        final LlmContentAccumulator exhausted = new LlmContentAccumulator(new TestBufferPool(CAPACITY, 0), 1L);

        final int value = exhausted.open();
        exhausted.append(buffer("abc"), 0, 3);

        assertThat(exhausted.truncated(value), is(true));
        assertThat(exhausted.length(value), is(0));
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

        assertThat(text(value), equalTo("de"));
    }

    private String text(
        int value)
    {
        final byte[] bytes = new byte[accumulator.length(value)];

        accumulator.buffer().getBytes(accumulator.offset(value), bytes);

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
