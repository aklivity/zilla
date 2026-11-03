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

import java.util.Arrays;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

/**
 * Accumulates the values of a response so that they can be repeated by later events, such as a completion
 * event that repeats every output item.
 * <p>
 * All values share a single pool slot, so the bound is the slot capacity of the pool.
 * A value that does not fit within the remaining capacity of the slot is truncated: it stores nothing and
 * is repeated as empty, while later values that still fit remain stored.
 * Appending reuses the slot and the index arrays, so only growth of the index arrays allocates.
 */
final class LlmContentAccumulator
{
    private static final int NONE = -1;
    private static final int CAPACITY = 8;

    private final BufferPool pool;
    private final long streamId;

    private int slot = NO_SLOT;
    private int limit;
    private int count;
    private int current = NONE;
    private int[] offsets;
    private int[] lengths;
    private boolean[] truncated;

    LlmContentAccumulator(
        BufferPool pool,
        long streamId)
    {
        this.pool = pool;
        this.streamId = streamId;
        this.offsets = new int[CAPACITY];
        this.lengths = new int[CAPACITY];
        this.truncated = new boolean[CAPACITY];
    }

    int bound()
    {
        return pool.slotCapacity();
    }

    int open()
    {
        if (count == offsets.length)
        {
            offsets = Arrays.copyOf(offsets, count << 1);
            lengths = Arrays.copyOf(lengths, count << 1);
            truncated = Arrays.copyOf(truncated, count << 1);
        }

        offsets[count] = limit;
        lengths[count] = 0;
        truncated[count] = false;
        current = count;

        return count++;
    }

    void append(
        DirectBufferEx source,
        int offset,
        int length)
    {
        if (current != NONE && !truncated[current] && length > 0)
        {
            if (slot == NO_SLOT)
            {
                slot = pool.acquire(streamId);
            }

            if (slot == NO_SLOT || limit + length > pool.slotCapacity())
            {
                truncated[current] = true;
                limit = offsets[current];
                lengths[current] = 0;
            }
            else
            {
                pool.buffer(slot).putBytes(limit, source, offset, length);
                limit += length;
                lengths[current] += length;
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

    boolean truncated(
        int value)
    {
        return truncated[value];
    }

    int length(
        int value)
    {
        return truncated[value] ? 0 : lengths[value];
    }

    int offset(
        int value)
    {
        return offsets[value];
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
    }
}
