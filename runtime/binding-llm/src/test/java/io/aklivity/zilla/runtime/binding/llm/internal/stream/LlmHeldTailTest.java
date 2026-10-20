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

public class LlmHeldTailTest
{
    private static final int CAPACITY = 16;

    private final TestBufferPool pool = new TestBufferPool(CAPACITY, 1);
    private final LlmHeldTail tail = new LlmHeldTail(pool);

    @Test
    public void shouldHoldAppendedBytes()
    {
        assertThat(tail.held(), is(false));
        assertThat(tail.buffer(), nullValue());

        assertThat(tail.append(1L, buffer("abc"), 0, 3), is(true));
        assertThat(tail.append(1L, buffer("xdefx"), 1, 3), is(true));

        assertThat(tail.held(), is(true));
        assertThat(tail.length(), is(6));
        assertThat(tail.remaining(), is(6));
        assertThat(text(), equalTo("abcdef"));
    }

    @Test
    public void shouldConsumeFromFront()
    {
        tail.append(1L, buffer("abcdef"), 0, 6);
        tail.consumed(4);

        assertThat(tail.remaining(), is(2));
        assertThat(tail.offset(), is(4));
        assertThat(tail.length(), is(6));
    }

    @Test
    public void shouldRejectAppendBeyondBoundAndKeepHeldBytes()
    {
        tail.append(1L, buffer("0123456789"), 0, 10);

        assertThat(tail.append(1L, buffer("abcdefghij"), 0, 10), is(false));
        assertThat(tail.length(), is(10));
        assertThat(text(), equalTo("0123456789"));
    }

    @Test
    public void shouldIgnoreEmptyAppend()
    {
        assertThat(tail.append(1L, buffer("abc"), 0, 0), is(true));
        assertThat(tail.held(), is(false));
        assertThat(pool.acquiredSlots(), is(0));
    }

    @Test
    public void shouldRejectAppendWhenNoSlotAvailable()
    {
        final LlmHeldTail exhausted = new LlmHeldTail(new TestBufferPool(CAPACITY, 0));

        assertThat(exhausted.append(1L, buffer("abc"), 0, 3), is(false));
        assertThat(exhausted.held(), is(false));
    }

    @Test
    public void shouldReleaseSlot()
    {
        tail.append(1L, buffer("abc"), 0, 3);
        tail.consumed(1);
        tail.release();

        assertThat(pool.acquiredSlots(), is(0));
        assertThat(tail.held(), is(false));
        assertThat(tail.remaining(), is(0));
        assertThat(tail.offset(), is(0));

        tail.append(1L, buffer("de"), 0, 2);

        assertThat(text(), equalTo("de"));
    }

    private String text()
    {
        final byte[] bytes = new byte[tail.length()];

        tail.buffer().getBytes(0, bytes);

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
