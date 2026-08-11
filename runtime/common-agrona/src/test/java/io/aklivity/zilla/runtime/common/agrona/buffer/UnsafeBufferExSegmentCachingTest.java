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
import java.nio.ByteBuffer;

import org.junit.Test;

/**
 * {@link UnsafeBufferEx#wrap(byte[])} and {@link UnsafeBufferEx#wrap(byte[], int, int)} re-wrap the same
 * array on every fetched record in some model field-capture paths (e.g. an empty-value re-wrap between
 * fields); {@link MemorySegment#ofArray(byte[])} depends only on the array's identity, so re-wrapping the
 * same array must reuse the previously derived segment rather than deriving a fresh one every call.
 */
public class UnsafeBufferExSegmentCachingTest
{
    @Test
    public void shouldReuseSegmentWhenSameArrayRewrapped()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[0]);
        byte[] array = {0x2a, 0x2b};

        buffer.wrap(array);
        MemorySegment first = buffer.segment();
        buffer.wrap(array);
        MemorySegment second = buffer.segment();

        assertSame(first, second);
        assertEquals((byte) 0x2a, second.get(JAVA_BYTE, 0));
    }

    @Test
    public void shouldReuseSegmentWhenSameArrayRewrappedWithOffset()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[0]);
        byte[] array = {0x11, 0x22, 0x33, 0x44};

        buffer.wrap(array, 1, 2);
        MemorySegment first = buffer.segment();
        buffer.wrap(array, 0, 4);
        MemorySegment second = buffer.segment();

        assertSame(first, second);
    }

    @Test
    public void shouldRecomputeSegmentWhenArrayChanges()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[0]);

        buffer.wrap(new byte[] {0x55});
        MemorySegment first = buffer.segment();
        buffer.wrap(new byte[] {0x66});
        MemorySegment second = buffer.segment();

        assertNotSame(first, second);
        assertEquals((byte) 0x66, second.get(JAVA_BYTE, 0));
    }

    // A cache keyed off a field dedicated to wrap(byte[]...) alone goes stale here: an intervening
    // wrap of a different kind (any of the other overloads) reassigns `segment` without touching that
    // field, so re-wrapping the same array afterwards would wrongly skip recomputation and return the
    // intervening wrap's segment instead of one for `array`.
    @Test
    public void shouldRecomputeSegmentAfterIntermediateNonArrayWrap()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[0]);
        byte[] array = {0x77};

        buffer.wrap(array);
        buffer.wrap(ByteBuffer.allocateDirect(16));
        buffer.wrap(array);
        MemorySegment after = buffer.segment();

        assertEquals(1, after.byteSize());
        assertEquals((byte) 0x77, after.get(JAVA_BYTE, 0));
    }
}
