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
 * Holds the bytes that complete a section until the downstream credit allows them to be released.
 * <p>
 * The bytes share a single pool slot, so the bound is the slot capacity of the pool.
 * An append that does not fit leaves the held bytes unchanged and reports that the bound was exceeded.
 * Released bytes are consumed from the front, so a partial release resumes where it stopped.
 */
final class LlmHeldTail
{
    private final BufferPool pool;

    private int slot = NO_SLOT;
    private int limit;
    private int offset;

    LlmHeldTail(
        BufferPool pool)
    {
        this.pool = pool;
    }

    boolean append(
        long streamId,
        DirectBufferEx source,
        int sourceOffset,
        int length)
    {
        return append(streamId, source, sourceOffset, length, Integer.MAX_VALUE);
    }

    boolean append(
        long streamId,
        DirectBufferEx source,
        int sourceOffset,
        int length,
        int bound)
    {
        boolean fits = true;

        if (length > 0)
        {
            if (slot == NO_SLOT)
            {
                slot = pool.acquire(streamId);
            }

            fits = slot != NO_SLOT && limit + length <= Math.min(pool.slotCapacity(), bound);

            if (fits)
            {
                pool.buffer(slot).putBytes(limit, source, sourceOffset, length);
                limit += length;
            }
        }

        return fits;
    }

    int remaining()
    {
        return limit - offset;
    }

    int length()
    {
        return limit;
    }

    boolean held()
    {
        return limit > 0;
    }

    int offset()
    {
        return offset;
    }

    MutableDirectBufferEx buffer()
    {
        return slot != NO_SLOT ? pool.buffer(slot) : null;
    }

    void consumed(
        int length)
    {
        offset += length;
    }

    void release()
    {
        if (slot != NO_SLOT)
        {
            pool.release(slot);
            slot = NO_SLOT;
        }

        limit = 0;
        offset = 0;
    }
}
