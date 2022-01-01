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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generated;

import static java.nio.ByteBuffer.allocateDirect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.UnionChildFW;

public class UnionChildFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final UnionChildFW.Builder flyweightRW = new UnionChildFW.Builder();
    private final UnionChildFW flyweightRO = new UnionChildFW();
    private static final int KIND_WIDTH8 = 8;
    private static final int KIND_WIDTH16 = 16;
    private static final int KIND_WIDTH32 = 32;

    static int setAllTestValuesCase8(
        MutableDirectBuffer buffer,
        final int offset)
    {
        int pos = offset;
        buffer.putLong(pos, 10);
        buffer.putByte(pos += Long.BYTES, (byte) 8);
        buffer.putByte(pos += 1, (byte) 8);
        return pos - offset + Byte.BYTES; // size of bites that are encoded
    }

    static int setAllTestValuesCase16(
        MutableDirectBuffer buffer,
        final int offset)
    {
        int pos = offset;
        buffer.putLong(pos, 10);
        buffer.putByte(pos += Long.BYTES, (byte) 16);
        buffer.putShort(pos += 1, (short) 16);
        return pos - offset + Short.BYTES;
    }

    static int setAllTestValuesCase32(
        MutableDirectBuffer buffer,
        final int offset)
    {
        int pos = offset;
        buffer.putLong(pos, 10);
        buffer.putByte(pos += Long.BYTES, (byte) 32);
        buffer.putInt(pos += 1, 32);
        return pos - offset + Integer.BYTES;
    }

    static void assertAllTestValuesReadCase8(
        int offset,
        UnionChildFW flyweight)
    {
        assertEquals(KIND_WIDTH8, flyweight.kind());
        assertEquals(8, flyweight.width8());
        assertEquals(offset + Long.BYTES + Byte.BYTES + Byte.BYTES, flyweight.limit());
    }

    static void assertAllTestValuesReadCase16(
        int offset,
        UnionChildFW flyweight)
    {
        assertEquals(KIND_WIDTH16, flyweight.kind());
        assertEquals(16, flyweight.width8());
        assertEquals(offset + Long.BYTES + Byte.BYTES + Short.BYTES, flyweight.limit());
    }

    static void assertAllTestValuesReadCase32(
        int offset,
        UnionChildFW flyweight)
    {
        assertEquals(KIND_WIDTH32, flyweight.kind());
        assertEquals(32, flyweight.width8());
        assertEquals(offset + Long.BYTES + Byte.BYTES + Integer.BYTES, flyweight.limit());
    }

    @Test
    public void shouldNotTryWrapWhenIncompleteCase1()
    {
        int size = setAllTestValuesCase8(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotWrapWhenIncompleteCase1()
    {
        int size = setAllTestValuesCase8(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            flyweightRO.wrap(buffer,  10, maxLimit);
        }
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientCase1()
    {
        int size = setAllTestValuesCase8(buffer, 10);
        assertSame(flyweightRO, flyweightRO.tryWrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldWrapWhenLengthSufficientCase1()
    {
        int size = setAllTestValuesCase8(buffer, 10);
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldTryWrapAndReadAllValuesCase8() throws Exception
    {
        final int offset = 1;
        setAllTestValuesCase8(buffer, offset);
        UnionChildFW flyweight = flyweightRO.tryWrap(buffer, offset, buffer.capacity());
        assertNotNull(flyweight);
        assertAllTestValuesReadCase8(offset, flyweight);
    }

    @Test
    public void shouldWrapAndReadAllValuesCase1() throws Exception
    {
        final int offset = 1;
        setAllTestValuesCase8(buffer, offset);
        UnionChildFW flyweight = flyweightRO.wrap(buffer, offset, buffer.capacity());
        assertAllTestValuesReadCase8(offset, flyweight);
    }

    @Test
    public void shouldSetWidth8()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(10)
            .width8((byte) 8)
            .build()
            .limit();
        UnionChildFW unionChild = flyweightRO.wrap(buffer,  0,  limit);
        assertEquals((byte) 8, unionChild.width8());
        assertEquals(KIND_WIDTH8, unionChild.kind());
    }

    @Test
    public void shouldSetWidth16()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(10)
            .width16((short) 16)
            .build()
            .limit();
        UnionChildFW unionChild = flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(16, unionChild.width16());
        assertEquals(KIND_WIDTH16, unionChild.kind());
    }

    @Test
    public void shouldSetWidth32()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(10)
            .width32(32)
            .build()
            .limit();
        UnionChildFW unionChild = flyweightRO.wrap(buffer,  0,  limit);
        assertEquals(32, unionChild.width32());
        assertEquals(KIND_WIDTH32, unionChild.kind());
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetKindBeforeSettingParentField()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .width8((byte) 8)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenParentFieldIsNotSet()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .build();
    }
}
