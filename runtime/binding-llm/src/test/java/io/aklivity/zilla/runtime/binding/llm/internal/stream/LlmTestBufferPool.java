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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

final class LlmTestBufferPool implements BufferPool
{
    private final int capacity;
    private final int slots;
    private final List<MutableDirectBufferEx> buffers;
    private final boolean[] acquired;

    LlmTestBufferPool(
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
