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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

/**
 * Accumulates the values of a response so that they can be repeated by later events, such as a completion
 * event that repeats every output item.
 * <p>
 * Values are stored in one pool slot while they fit, so the common response keeps nothing on the heap. Content is
 * unbounded by protocol, so when the slot cannot hold what is accumulated the records move to a heap region that
 * grows as needed, and no value is ever truncated. Each value is a record of a kind byte, a length and its bytes,
 * so the storage describes itself. A value is found by walking the records from the last one found.
 */
final class LlmContentAccumulator
{
    static final int NONE = -1;

    private static final int LENGTH_OFFSET = Byte.BYTES;
    private static final int HEADER = Byte.BYTES + Integer.BYTES;
    private static final int KIND_MASK = 0x7f;

    private final BufferPool pool;
    private final long streamId;

    private int slot = NO_SLOT;
    private MutableDirectBufferEx spill;
    private int limit;
    private int count;
    private int current = NONE;
    private int cursorValue;
    private int cursorOffset;

    LlmContentAccumulator(
        BufferPool pool,
        long streamId)
    {
        this.pool = pool;
        this.streamId = streamId;
    }

    int open()
    {
        return open(0);
    }

    int open(
        int kind)
    {
        if (slot == NO_SLOT && spill == null)
        {
            slot = pool.acquire(streamId);
        }

        ensure(HEADER);

        final MutableDirectBufferEx buffer = storage();

        buffer.putByte(limit, (byte) (kind & KIND_MASK));
        buffer.putInt(limit + LENGTH_OFFSET, 0);

        current = limit;
        limit += HEADER;

        return count++;
    }

    void append(
        DirectBufferEx source,
        int offset,
        int length)
    {
        if (length > 0 && reserve(length))
        {
            storage().putBytes(limit, source, offset, length);
            commit(length);
        }
    }

    void appendInt(
        int data)
    {
        if (reserve(Integer.BYTES))
        {
            storage().putInt(limit, data);
            commit(Integer.BYTES);
        }
    }

    void appendText(
        String text)
    {
        if (text == null)
        {
            appendInt(NONE);
        }
        else
        {
            final int length = MutableDirectBufferEx.utf8Length(text, 0, text.length());

            if (reserve(Integer.BYTES + length))
            {
                final MutableDirectBufferEx buffer = storage();

                buffer.putInt(limit, length);
                buffer.putStringWithoutLengthUtf8(limit + Integer.BYTES, text, 0, text.length());
                commit(Integer.BYTES + length);
            }
        }
    }

    void close()
    {
        current = NONE;
    }

    int count()
    {
        return count;
    }

    int kind(
        int value)
    {
        return exists(value) ? storage().getByte(locate(value)) & KIND_MASK : 0;
    }

    boolean absent(
        int value)
    {
        return !exists(value);
    }

    int length(
        int value)
    {
        return exists(value) ? storage().getInt(locate(value) + LENGTH_OFFSET) : 0;
    }

    int offset(
        int value)
    {
        return exists(value) ? locate(value) + HEADER : 0;
    }

    void putInt(
        int value,
        int position,
        int data)
    {
        if (within(value, position))
        {
            storage().putInt(locate(value) + HEADER + position, data);
        }
    }

    int getInt(
        int value,
        int position)
    {
        return within(value, position) ? storage().getInt(locate(value) + HEADER + position) : 0;
    }

    MutableDirectBufferEx buffer()
    {
        return slot != NO_SLOT || spill != null ? storage() : null;
    }

    void release()
    {
        if (slot != NO_SLOT)
        {
            pool.release(slot);
            slot = NO_SLOT;
        }

        spill = null;
        limit = 0;
        count = 0;
        current = NONE;
        cursorValue = 0;
        cursorOffset = 0;
    }

    private boolean reserve(
        int length)
    {
        final boolean reserved = current != NONE;

        if (reserved)
        {
            ensure(length);
        }

        return reserved;
    }

    private void ensure(
        int length)
    {
        final int required = limit + length;

        if (required > capacity())
        {
            final MutableDirectBufferEx grown =
                new UnsafeBufferEx(new byte[Math.max(required, Math.max(capacity(), 1) << 1)]);

            if (limit > 0)
            {
                grown.putBytes(0, storage(), 0, limit);
            }

            if (slot != NO_SLOT)
            {
                pool.release(slot);
                slot = NO_SLOT;
            }

            spill = grown;
        }
    }

    private int capacity()
    {
        return spill != null ? spill.capacity() : slot != NO_SLOT ? pool.slotCapacity() : 0;
    }

    private MutableDirectBufferEx storage()
    {
        return spill != null ? spill : pool.buffer(slot);
    }

    private void commit(
        int length)
    {
        final MutableDirectBufferEx buffer = storage();

        buffer.putInt(current + LENGTH_OFFSET, buffer.getInt(current + LENGTH_OFFSET) + length);
        limit += length;
    }

    private boolean within(
        int value,
        int position)
    {
        return exists(value) && position >= 0 && position + Integer.BYTES <= length(value);
    }

    private boolean exists(
        int value)
    {
        return (slot != NO_SLOT || spill != null) && value >= 0 && value < count;
    }

    private int locate(
        int value)
    {
        final MutableDirectBufferEx buffer = storage();

        int index = cursorValue;
        int offset = cursorOffset;

        if (value < index)
        {
            index = 0;
            offset = 0;
        }

        while (index < value)
        {
            offset += HEADER + buffer.getInt(offset + LENGTH_OFFSET);
            index++;
        }

        cursorValue = index;
        cursorOffset = offset;

        return offset;
    }
}
