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
package io.aklivity.zilla.runtime.engine.internal.buffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.agrona.MutableDirectBuffer;
import org.junit.Test;

public class DefaultBufferPoolTest
{
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSlotCapacityNotPowerOfTwo()
    {
        new DefaultBufferPool(1024, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectTotalCapacityNotPowerOfTwo()
    {
        new DefaultBufferPool(10000, 1024);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSlotCapacityGreaterThanTotalCapacity()
    {
        new DefaultBufferPool(256, 512);
    }

    @Test
    public void acquireShouldAllocateSlot() throws Exception
    {
        DefaultBufferPool slab = new DefaultBufferPool(16, 4);
        int slot = slab.acquire(123);
        assertTrue(slot >= 0 && slot < 4);
    }

    @Test
    public void acquireShouldAllocateDifferentSlotsForDifferentStreams() throws Exception
    {
        DefaultBufferPool slab = new DefaultBufferPool(512 * 1024, 1024);
        int slot1 = slab.acquire(111);
        assertTrue(slot1 >= 0);
        int slot2 = slab.acquire(112);
        assertTrue(slot2 >= 0);

        assertNotEquals(slot1, slot2);
    }

    @Test
    public void acquireShouldAllocateDifferentSlotsForDifferentStreamsWithSameHashcode() throws Exception
    {
        DefaultBufferPool slab = new DefaultBufferPool(512 * 1024, 1024);

        int slot1 = slab.acquire(1);
        assertTrue(slot1 >= 0);

        int slot2 = slab.acquire(16);
        assertTrue(slot2 >= 0);

        assertNotEquals(slot1, slot2);
    }

    @Test
    public void acquireShouldReportOutOfMemory() throws Exception
    {
        DefaultBufferPool slab = new DefaultBufferPool(256, 16);
        int slot = 0;
        int i;
        for (i = 0; i < 16; i++)
        {
            int streamId = 111 + i;
            slot = slab.acquire(streamId);
            assertTrue(slot >= 0);
        }
        slot = slab.acquire(111 + i);
        assertEquals(DefaultBufferPool.NO_SLOT, slot);
    }

    @Test
    public void bufferShouldReturnCorrectlySizedBuffer() throws Exception
    {
        DefaultBufferPool slab = new DefaultBufferPool(256, 16);
        int slot = slab.acquire(124123490L);
        MutableDirectBuffer buffer = slab.buffer(slot);
        buffer.putInt(0, 123);
        assertEquals(123, buffer.getInt(0));
        assertEquals(16, buffer.capacity());
    }

    @Test
    public void freeShouldMakeSlotAvailableForReuse() throws Exception
    {
        DefaultBufferPool slab = new DefaultBufferPool(16 * 1024, 1024);
        int slot = 0;
        int i;
        for (i = 0; i < 16; i++)
        {
            int streamId = 111 + i;
            slot = slab.acquire(streamId);
            assertTrue(slot >= 0);
        }
        int slotBad = slab.acquire(111 + i);
        assertEquals(DefaultBufferPool.NO_SLOT, slotBad);
        slab.release(slot);
        slot = slab.acquire(111 + i);
        assertNotEquals(DefaultBufferPool.NO_SLOT, slot);
    }

}

