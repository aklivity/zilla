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
package io.aklivity.zilla.engine.drive.internal.buffer;

import static java.lang.Integer.numberOfTrailingZeros;
import static org.agrona.BitUtil.isPowerOfTwo;

import java.nio.ByteBuffer;
import java.util.BitSet;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Hashing;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.engine.drive.cog.buffer.BufferPool;

/**
 * A chunk of shared memory for temporary storage of data. This is logically segmented into a set of
 * slots of equal size. Methods are provided for acquiring a slot, getting a poolBuffer that can be used
 * to store data in it, and releasing the slot once it is no longer needed.
 * <b>Each instance of this class is assumed to be used by one and only one thread.</b>
 */
public class DefaultBufferPool implements BufferPool
{
    private final MutableDirectBuffer slotBuffer = new UnsafeBuffer(new byte[0]);

    private final int slotCapacity;
    private final int slotCount;
    private final AtomicBuffer poolBuffer;
    private final ByteBuffer slotByteBuffer;

    private final int bitsPerSlot;
    private final int hashMask;
    private final BitSet used;
    private final MutableInteger availableSlots;
    private final int usedIndex;


    public DefaultBufferPool(
        int poolCapacity,
        int slotCapacity)
    {
        this(slotCapacity, poolCapacity / slotCapacity,
                ByteBuffer.allocate((slotCapacity + Long.BYTES) * poolCapacity / slotCapacity + Integer.BYTES));
    }

    public DefaultBufferPool(
        int slotCapacity,
        int slotCount,
        ByteBuffer poolByteBuffer)
    {
        if (!isZeroOrPowerOfTwo(slotCapacity))
        {
            throw new IllegalArgumentException("slotCapacity is not a power of 2");
        }
        if (!isZeroOrPowerOfTwo(slotCount))
        {
            throw new IllegalArgumentException("slotCount is not a power of 2");
        }
        final int capacity = slotCapacity * slotCount;
        final int trailerLength = Long.BYTES * slotCount + Integer.BYTES;
        final int totalCapacity = capacity + trailerLength;
        if (poolByteBuffer.capacity() != totalCapacity)
        {
            throw new IllegalArgumentException(String.format("poolBuffer capacity not equal to %x", totalCapacity));
        }
        this.slotCapacity = slotCapacity;
        this.slotCount = slotCount;
        this.bitsPerSlot = numberOfTrailingZeros(slotCapacity);
        this.hashMask = slotCount - 1;
        this.poolBuffer = new UnsafeBuffer(poolByteBuffer);
        this.slotByteBuffer = poolByteBuffer.duplicate();

        this.used = new BitSet(slotCount);
        this.availableSlots = new MutableInteger(slotCount);
        this.usedIndex = capacity;
    }

    public int acquiredSlots()
    {
        return used.cardinality();
    }

    public int slotCount()
    {
        return slotCount;
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
        if (availableSlots.value == 0)
        {
            return NO_SLOT;
        }
        int slot = Hashing.hash(streamId, hashMask);
        while (used.get(slot))
        {
            slot = ++slot & hashMask;
        }
        used.set(slot);
        availableSlots.value--;
        poolBuffer.putLongOrdered(usedIndex + (slot << 3), streamId);

        return slot;
    }

    @Override
    public MutableDirectBuffer buffer(
        int slot)
    {
        assert used.get(slot);
        slotBuffer.wrap(poolBuffer, slot << bitsPerSlot, slotCapacity);
        return slotBuffer;
    }

    @Override
    public ByteBuffer byteBuffer(
        int slot)
    {
        assert used.get(slot);
        final int slotOffset = slot << bitsPerSlot;
        slotByteBuffer.clear();
        slotByteBuffer.position(slotOffset);
        slotByteBuffer.limit(slotOffset + slotCapacity);
        return slotByteBuffer;
    }

    @Override
    public MutableDirectBuffer buffer(
        int slot,
        int offset)
    {
        assert used.get(slot);
        final long slotAddressOffset = poolBuffer.addressOffset() + (slot << bitsPerSlot);
        slotBuffer.wrap(slotAddressOffset + offset, slotCapacity);
        return slotBuffer;
    }

    /**
     * Releases a slot so it may be used by other streams
     * @param slot - Id of a previously acquired slot
     */
    @Override
    public void release(
        int slot)
    {
        assert used.get(slot);
        used.clear(slot);
        availableSlots.value++;
        poolBuffer.putLongOrdered(usedIndex + (slot << 3), 0L);
    }

    @Override
    public BufferPool duplicate()
    {
        return new DefaultBufferPool(this);
    }

    public DirectBuffer poolBuffer()
    {
        return poolBuffer;
    }

    private DefaultBufferPool(
        DefaultBufferPool that)
    {
        this.availableSlots = that.availableSlots;
        this.bitsPerSlot = that.bitsPerSlot;
        this.hashMask = that.hashMask;
        this.poolBuffer = that.poolBuffer;
        this.slotCapacity = that.slotCapacity;
        this.slotCount = that.slotCount;
        this.used = that.used;
        this.usedIndex = that.usedIndex;
        this.slotByteBuffer = that.slotByteBuffer.duplicate();
    }

    private static boolean isZeroOrPowerOfTwo(int value)
    {
        return value == 0 || isPowerOfTwo(value);
    }
}
