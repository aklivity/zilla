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

final class LlmRequestHold
{
    static final int TAIL = 0;
    static final int HOLD = 1;
    static final int DEFER = 2;

    private static final int REGIONS = 3;

    private final BufferPool pool;
    private final long streamId;
    private final int capacity;
    private final int[] lengths;

    private int slot = NO_SLOT;
    private int total;

    LlmRequestHold(
        BufferPool pool,
        long streamId)
    {
        this.pool = pool;
        this.streamId = streamId;
        this.capacity = pool.slotCapacity();
        this.lengths = new int[REGIONS];
        this.slot = NO_SLOT;
    }

    int length(
        int region)
    {
        return lengths[region];
    }

    int offset(
        int region)
    {
        int offset = 0;

        for (int index = 0; index < region; index++)
        {
            offset += lengths[index];
        }

        return offset;
    }

    MutableDirectBufferEx buffer()
    {
        return slot != NO_SLOT ? pool.buffer(slot) : null;
    }

    boolean open()
    {
        if (slot == NO_SLOT)
        {
            slot = pool.acquire(streamId);
        }

        return slot != NO_SLOT;
    }

    boolean room(
        int length)
    {
        return total + length <= capacity;
    }

    boolean append(
        int region,
        DirectBufferEx source,
        int offset,
        int length)
    {
        final boolean appended = room(length) && open();

        if (appended)
        {
            final MutableDirectBufferEx buffer = pool.buffer(slot);
            final int end = offset(region) + lengths[region];

            shift(buffer, end, length);
            buffer.putBytes(end, source, offset, length);
            lengths[region] += length;
            total += length;
        }

        return appended;
    }

    boolean appendUtf8(
        int region,
        CharSequence chars,
        int from,
        int to)
    {
        final int length = MutableDirectBufferEx.utf8Length(chars, from, to);
        final boolean appended = room(length) && open();

        if (appended)
        {
            final MutableDirectBufferEx buffer = pool.buffer(slot);
            final int end = offset(region) + lengths[region];

            shift(buffer, end, length);
            buffer.putStringWithoutLengthUtf8(end, chars, from, to);
            lengths[region] += length;
            total += length;
        }

        return appended;
    }

    boolean replace(
        int region,
        DirectBufferEx source,
        int offset,
        int length)
    {
        final int delta = length - lengths[region];
        final boolean replaced = delta <= 0 || room(delta) && open();

        if (replaced && (length > 0 || slot != NO_SLOT))
        {
            final MutableDirectBufferEx buffer = pool.buffer(slot);
            final int start = offset(region);
            final int end = start + lengths[region];

            if (delta > 0)
            {
                shift(buffer, end, delta);
            }

            buffer.putBytes(start, source, offset, length);

            if (delta < 0)
            {
                buffer.putBytes(end + delta, buffer, end, total - end);
            }

            lengths[region] = length;
            total += delta;
        }

        return replaced;
    }

    void clear(
        int region)
    {
        if (lengths[region] > 0)
        {
            final MutableDirectBufferEx buffer = pool.buffer(slot);
            final int start = offset(region);
            final int end = start + lengths[region];

            buffer.putBytes(start, buffer, end, total - end);
            total -= lengths[region];
            lengths[region] = 0;
        }

        if (total == 0)
        {
            release();
        }
    }

    void release()
    {
        if (slot != NO_SLOT)
        {
            pool.release(slot);
            slot = NO_SLOT;
        }

        for (int index = 0; index < REGIONS; index++)
        {
            lengths[index] = 0;
        }

        total = 0;
    }

    private void shift(
        MutableDirectBufferEx buffer,
        int end,
        int length)
    {
        if (total > end)
        {
            buffer.putBytes(end + length, buffer, end, total - end);
        }
    }
}
