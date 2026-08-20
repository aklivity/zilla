/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.common.agrona.buffer;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.lang.foreign.MemorySegment;

import org.junit.Test;

/**
 * {@link ExpandableDirectByteBufferEx#segment()} caches its derived {@link MemorySegment} and must only
 * recompute it when the backing buffer's identity or {@link ExpandableDirectByteBufferEx#capacity()}
 * changes — either signals a reallocation, so a segment computed while neither has changed is still valid.
 */
public class ExpandableDirectByteBufferExTest
{
    @Test
    public void shouldReuseSegmentWhenCapacityUnchanged()
    {
        ExpandableDirectByteBufferEx buffer = new ExpandableDirectByteBufferEx(64);
        buffer.putByte(0, (byte) 0x2a);

        MemorySegment first = buffer.segment();
        MemorySegment second = buffer.segment();

        assertSame(first, second);
        assertEquals((byte) 0x2a, first.get(JAVA_BYTE, 0));
    }

    @Test
    public void shouldRecomputeSegmentAfterGrowth()
    {
        ExpandableDirectByteBufferEx buffer = new ExpandableDirectByteBufferEx(8);
        buffer.putByte(0, (byte) 0x11);
        MemorySegment before = buffer.segment();
        int capacityBefore = buffer.capacity();

        // beyond the initial capacity, forcing ExpandableDirectByteBuffer to reallocate
        buffer.putByte(256, (byte) 0x22);
        MemorySegment after = buffer.segment();

        assertNotSame(before, after);
        assertEquals(capacityBefore, before.byteSize());
        assertEquals(buffer.capacity(), after.byteSize());
        assertEquals((byte) 0x11, after.get(JAVA_BYTE, 0));
        assertEquals((byte) 0x22, after.get(JAVA_BYTE, 256));
    }
}
