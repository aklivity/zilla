/*
 * Copyright 2021-2021 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.cog.buffer;

import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;

public final class CountingBufferPool implements BufferPool
{
    private final BufferPool bufferPool;
    private final LongSupplier acquires;
    private final LongSupplier releases;

    public CountingBufferPool(
        BufferPool bufferPool,
        LongSupplier acquires,
        LongSupplier releases)
    {
        this.bufferPool = bufferPool;
        this.acquires = acquires;
        this.releases = releases;
    }

    @Override
    public int slotCapacity()
    {
        return bufferPool.slotCapacity();
    }

    @Override
    public int acquire(
        long streamId)
    {
        final int slot = bufferPool.acquire(streamId);

        if (slot != NO_SLOT)
        {
            acquires.getAsLong();
        }

        return slot;
    }

    @Override
    public MutableDirectBuffer buffer(
        int slot)
    {
        return bufferPool.buffer(slot);
    }

    @Override
    public ByteBuffer byteBuffer(
        int slot)
    {
        return bufferPool.byteBuffer(slot);
    }

    @Override
    public MutableDirectBuffer buffer(
        int slot,
        int offset)
    {
        return bufferPool.buffer(slot, offset);
    }

    @Override
    public void release(
        int slot)
    {
        bufferPool.release(slot);

        if (slot != NO_SLOT)
        {
            releases.getAsLong();
        }
    }

    @Override
    public BufferPool duplicate()
    {
        return new CountingBufferPool(bufferPool.duplicate(), acquires, releases);
    }

    @Override
    public int acquiredSlots()
    {
        return bufferPool.acquiredSlots();
    }
}
