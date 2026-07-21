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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.stream;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

// A minimal in-memory BufferPool test double: real, stable per-slot storage (unlike a Mockito
// stub, since decode/encode slot logic reads back what it previously wrote across separate calls).
final class TestBufferPool implements BufferPool
{
    private final int slotCapacity;
    private final Map<Integer, MutableDirectBufferEx> slots = new HashMap<>();

    private int nextSlot;

    TestBufferPool(
        int slotCapacity)
    {
        this.slotCapacity = slotCapacity;
    }

    @Override
    public int slotCapacity()
    {
        return slotCapacity;
    }

    @Override
    public int acquire(
        long streamId)
    {
        final int slot = nextSlot++;
        slots.put(slot, new UnsafeBufferEx(new byte[slotCapacity]));
        return slot;
    }

    @Override
    public MutableDirectBufferEx buffer(
        int slot)
    {
        return slots.get(slot);
    }

    @Override
    public ByteBuffer byteBuffer(
        int slot)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public MutableDirectBufferEx buffer(
        int slot,
        int offset)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void release(
        int slot)
    {
        slots.remove(slot);
    }

    @Override
    public BufferPool duplicate()
    {
        return this;
    }

    @Override
    public int acquiredSlots()
    {
        return slots.size();
    }
}
