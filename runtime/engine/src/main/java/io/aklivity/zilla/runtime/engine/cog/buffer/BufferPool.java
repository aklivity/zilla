/*
 * Copyright 2021-2022 Aklivity Inc.
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

import org.agrona.MutableDirectBuffer;

public interface BufferPool
{
    int NO_SLOT = -1;

    /**
     * Returns the capacity of each slot in the buffer pool
     *
     * @return the slot capacity
     */
    int slotCapacity();

    /**
     * Reserves a slot for use by the given stream
     *
     * @param streamId  the stream identifier
     *
     * @return  reference to the acquired slot, or {@code NO_SLOT} if all slots are in use
     */
    int acquire(long streamId);

    /**
     * Returns a buffer which can be used to write data into the given slot
     *
     * @param slot  reference to a previously acquired slot
     *
     * @return  a buffer suitable for <b>one-time use only</b>
     */
    MutableDirectBuffer buffer(int slot);

    /**
     * Returns a {@code ByteBuffer} which can be used to write data into the given slot
     *
     * @param slot  reference to a previously acquired slot
     *
     * @return  a {@code ByteBuffer} suitable for <b>one-time use only</b>
     */
    ByteBuffer byteBuffer(int slot);

    /**
     * Gets a buffer which can be used to write data into the given slot, at a specific offset
     *
     * @param slot  reference to a previously acquired slot
     * @param offset  the buffer offset into the slot
     *
     * @return  a buffer suitable for <b>one-time use only</b>
     */
    MutableDirectBuffer buffer(int slot, int offset);

    /**
     * Releases a slot so it may be used by other streams
     *
     * @param slot  reference to a previously acquired slot
     */
    void release(int slot);

    /**
     * Returns a {@code BufferPool} backed by the same storage but that can reference a different
     * slot in parallel with the original {@code BufferPool}.
     *
     * @return a duplicate {@code BufferPool}
     */
    BufferPool duplicate();

    /**
     * Returns the number of slots currently reserved in this buffer pool.
     *
     * @return  the number of reserved slots
     */
    int acquiredSlots();
}
