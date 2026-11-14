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
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

/**
 * Accumulates the values of a response so that they can be repeated by later events, such as a completion
 * event that repeats every output item.
 * <p>
 * All values share a single pool slot, so the bound is the slot capacity of the pool. Each value is a record
 * of a kind byte, a length and its bytes, so the slot describes itself and nothing is kept on the heap.
 * A value is found by walking the records from the last one found.
 * A value that does not fit within the remaining capacity of the slot is truncated: it stores nothing and
 * is repeated as empty, while later values that still fit remain stored. A value whose record header does
 * not fit is not opened.
 */
final class LlmContentAccumulator
{
    static final int NONE = -1;

    private static final int LENGTH_OFFSET = Byte.BYTES;
    private static final int HEADER = Byte.BYTES + Integer.BYTES;
    private static final int KIND_MASK = 0x7f;
    private static final int TRUNCATED = 0x80;

    private final BufferPool pool;
    private final long streamId;

    private int slot = NO_SLOT;
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

    int bound()
    {
        return pool.slotCapacity();
    }

    int open()
    {
        return open(0);
    }

    int open(
        int kind)
    {
        int value = NONE;

        if (slot == NO_SLOT)
        {
            slot = pool.acquire(streamId);
        }

        if (slot != NO_SLOT && limit + HEADER <= pool.slotCapacity())
        {
            final MutableDirectBufferEx buffer = pool.buffer(slot);

            buffer.putByte(limit, (byte) (kind & KIND_MASK));
            buffer.putInt(limit + LENGTH_OFFSET, 0);

            current = limit;
            limit += HEADER;
            value = count++;
        }
        else
        {
            current = NONE;
        }

        return value;
    }

    void append(
        DirectBufferEx source,
        int offset,
        int length)
    {
        if (length > 0 && reserve(length))
        {
            pool.buffer(slot).putBytes(limit, source, offset, length);
            commit(length);
        }
    }

    void appendInt(
        int data)
    {
        if (reserve(Integer.BYTES))
        {
            pool.buffer(slot).putInt(limit, data);
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
                final MutableDirectBufferEx buffer = pool.buffer(slot);

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
        return exists(value) ? pool.buffer(slot).getByte(locate(value)) & KIND_MASK : 0;
    }

    boolean truncated(
        int value)
    {
        return !exists(value) || (pool.buffer(slot).getByte(locate(value)) & TRUNCATED) != 0;
    }

    int length(
        int value)
    {
        return exists(value) ? pool.buffer(slot).getInt(locate(value) + LENGTH_OFFSET) : 0;
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
            pool.buffer(slot).putInt(locate(value) + HEADER + position, data);
        }
    }

    int getInt(
        int value,
        int position)
    {
        return within(value, position) ? pool.buffer(slot).getInt(locate(value) + HEADER + position) : 0;
    }

    MutableDirectBufferEx buffer()
    {
        return slot != NO_SLOT ? pool.buffer(slot) : null;
    }

    void release()
    {
        if (slot != NO_SLOT)
        {
            pool.release(slot);
            slot = NO_SLOT;
        }

        limit = 0;
        count = 0;
        current = NONE;
        cursorValue = 0;
        cursorOffset = 0;
    }

    private boolean reserve(
        int length)
    {
        boolean reserved = false;

        if (current != NONE)
        {
            final MutableDirectBufferEx buffer = pool.buffer(slot);
            final int flags = buffer.getByte(current);

            if ((flags & TRUNCATED) == 0)
            {
                if (limit + length > pool.slotCapacity())
                {
                    buffer.putByte(current, (byte) (flags | TRUNCATED));
                    buffer.putInt(current + LENGTH_OFFSET, 0);
                    limit = current + HEADER;
                }
                else
                {
                    reserved = true;
                }
            }
        }

        return reserved;
    }

    private void commit(
        int length)
    {
        final MutableDirectBufferEx buffer = pool.buffer(slot);

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
        return slot != NO_SLOT && value >= 0 && value < count;
    }

    private int locate(
        int value)
    {
        final MutableDirectBufferEx buffer = pool.buffer(slot);

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
