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

import static io.aklivity.zilla.build.maven.plugins.cog.internal.generated.FlyweightTest.putMediumInt;
import static java.nio.ByteBuffer.allocateDirect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.IntegerFixedArraysFW;

public class IntegerFixedArraysFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(199))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xFF);
        }
    };
    private final MutableDirectBuffer expected = new UnsafeBuffer(allocateDirect(199))
    {
        {
            setMemory(0, capacity(), (byte) 0xFF);
        }
    };
    private final IntegerFixedArraysFW.Builder flyweightRW = new IntegerFixedArraysFW.Builder();
    private final IntegerFixedArraysFW flyweightRO = new IntegerFixedArraysFW();

    static int setAllTestValues(MutableDirectBuffer buffer, int offset)
    {
        buffer.putByte(offset + 0, (byte) 0xFF); // uint8Array[1]
        buffer.putShort(offset + 1, (short) 2); // uint16Array[2]
        buffer.putShort(offset + 3, (short) 0xFFFF);
        putMediumInt(buffer, offset + 5, 3); // uint24Array[3]
        putMediumInt(buffer, offset + 8, 0x00FF_FFFF);
        putMediumInt(buffer, offset + 11, 1);
        buffer.putInt(offset + 14, 4); // uint32Array[4]
        buffer.putInt(offset + 18, 0xFFFF_FFFF);
        buffer.putInt(offset + 22, 1);
        buffer.putInt(offset + 26, 2);
        buffer.putLong(offset + 30, 8L); // uint64Array[8]
        buffer.putLong(offset + 38, 0x7FFF_FFFF_FFFF_FFFFL);
        buffer.putLong(offset + 46, 2L);
        buffer.putLong(offset + 54, 3L);
        buffer.putLong(offset + 62, 4L);
        buffer.putLong(offset + 70, 5L);
        buffer.putLong(offset + 78, 6L);
        buffer.putLong(offset + 86, 7L);

        buffer.putByte(offset + 94, (byte) -1); // anchor

        buffer.putByte(offset + 95, (byte) 127); // int8Array[1]
        buffer.putShort(offset + 96, (short) 2); // int16Array[2]
        buffer.putShort(offset + 98, (short) 0xFFFF);
        putMediumInt(buffer, offset + 100, 3); // int24Array[3]
        putMediumInt(buffer, offset + 103, 0x00FFFFFF);
        putMediumInt(buffer, offset + 106, -1);
        buffer.putInt(offset + 109, 4); // int32Array[4]
        buffer.putInt(offset + 113, 0xFFFF_FFFF);
        buffer.putInt(offset + 117, -2);
        buffer.putInt(offset + 121, -3);
        buffer.putLong(offset + 125, 8L); // int64Array[8]
        buffer.putLong(offset + 133, -1L);
        buffer.putLong(offset + 141, -2L);
        buffer.putLong(offset + 149, -3L);
        buffer.putLong(offset + 157, -4L);
        buffer.putLong(offset + 165, -5L);
        buffer.putLong(offset + 173, -6L);
        buffer.putLong(offset + 181, -7L);

        return 181 + 8;
    }

    static void assertAllTestValuesRead(IntegerFixedArraysFW flyweight)
    {
        PrimitiveIterator.OfInt uint8Array = flyweight.uint8Array();
        assertEquals(0xFF, uint8Array.nextInt());

        PrimitiveIterator.OfInt uint16Array = flyweight.uint16Array();
        assertEquals(2, uint16Array.nextInt());
        assertEquals(0xFFFF, uint16Array.nextInt());

        PrimitiveIterator.OfInt uint24Array = flyweight.uint24Array();
        assertEquals(3, uint24Array.nextInt());
        assertEquals(0x00FF_FFFF, uint24Array.nextInt());
        assertEquals(1, uint24Array.nextInt());

        PrimitiveIterator.OfLong uint32Array = flyweight.uint32Array();
        assertEquals(4, uint32Array.nextLong());
        assertEquals(0xFFFF_FFFFL, uint32Array.nextLong());
        assertEquals(1L, uint32Array.nextLong());
        assertEquals(2L, uint32Array.nextLong());

        PrimitiveIterator.OfLong uint64Array = flyweight.uint64Array();
        assertEquals(8L, uint64Array.nextLong());
        assertEquals(0x7FFF_FFFF_FFFF_FFFFL, uint64Array.nextLong());
        assertEquals(2L, uint64Array.nextLong());
        assertEquals(3L, uint64Array.nextLong());
        assertEquals(4L, uint64Array.nextLong());
        assertEquals(5L, uint64Array.nextLong());
        assertEquals(6L, uint64Array.nextLong());
        assertEquals(7L, uint64Array.nextLong());

        assertNull(flyweight.anchor().asString());

        PrimitiveIterator.OfInt int8Array = flyweight.int8Array();
        assertEquals(127, int8Array.nextInt());

        PrimitiveIterator.OfInt int16Array = flyweight.int16Array();
        assertEquals(2, int16Array.nextInt());
        assertEquals(-1, int16Array.nextInt());

        PrimitiveIterator.OfInt int24Array = flyweight.int24Array();
        assertEquals(3, int24Array.nextInt());
        assertEquals(-1, int24Array.nextInt());
        assertEquals(-1, int24Array.nextInt());

        PrimitiveIterator.OfInt int32Array = flyweight.int32Array();
        assertEquals(4, int32Array.nextInt());
        assertEquals(-1, int32Array.nextInt());
        assertEquals(-2, int32Array.nextInt());
        assertEquals(-3, int32Array.nextInt());

        PrimitiveIterator.OfLong int64Array = flyweight.int64Array();
        assertEquals(8L, int64Array.nextLong());
        assertEquals(-1L, int64Array.nextLong());
        assertEquals(-2L, int64Array.nextLong());
        assertEquals(-3L, int64Array.nextLong());
        assertEquals(-4L, int64Array.nextLong());
        assertEquals(-5L, int64Array.nextLong());
        assertEquals(-6L, int64Array.nextLong());
        assertEquals(-7L, int64Array.nextLong());
    }

    @Test
    public void shouldNotTryWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer, 10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            try
            {
                flyweightRO.wrap(buffer,  10, maxLimit);
                fail("Exception not thrown");
            }
            catch (Exception e)
            {
                if (!(e instanceof IndexOutOfBoundsException))
                {
                    fail("Unexpected exception " + e);
                }
            }
        }
    }

    @Test
    public void shouldTryWrapWhenLengthSufficient()
    {
        int size = setAllTestValues(buffer, 10);
        assertSame(flyweightRO, flyweightRO.tryWrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldWrapWhenLengthSufficient()
    {
        int size = setAllTestValues(buffer, 10);
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldTryWrapAndReadAllValues() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);
        flyweightRO.wrap(buffer, offset, buffer.capacity());
        assertAllTestValuesRead(flyweightRO);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUint16ArrayBeyondLimit()
    {
        flyweightRW.wrap(buffer, 10, 13)
            .appendUint8Array(10)
            .appendUint16Array((short) 0)
            .appendUint16Array((short) 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetUint16ArrayToNull()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendUint8Array(10)
            .uint16Array(null);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToIncompletelySetUint16ArrayUsingAppend()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendUint8Array(10)
            .appendUint16Array(15)
            .appendUint32Array(13);
    }

    @Test(expected =  IllegalArgumentException.class)
    public void shouldFailToIncompletelySetUint16ArrayUsingIterator()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendUint8Array(0)
            .uint16Array(IntStream.of(1).iterator());
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWithIncompletelySetUint16ArrayUsingAppend()
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendUint8Array(10)
            .appendUint16Array(15)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetInt32ArrayWithIteratorExceedingSize() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .uint8Array(IntStream.of(0xFF).iterator())
            .uint16Array(IntStream.of(3, 0xFFFF).iterator())
            .uint32Array(LongStream.of(10, 11, 0xFFFFFFFFL).iterator())
            .uint64Array(LongStream.of(20, 21, 22, 23).iterator())
            .anchor("anchor")
            .int8Array(IntStream.of(127).iterator())
            .int16Array(IntStream.of(3, 0xFFFF).iterator())
            .int32Array(IntStream.of(-10, -11, -12, -13).iterator()) // too many values
            .build();
    }

    @Test
    public void shouldSetAllValuesUsingAppend() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendUint8Array(0xFF)
            .appendUint16Array(2)
            .appendUint16Array(0xFFFF)
            .appendUint24Array(3)
            .appendUint24Array(0x00FF_FFFF)
            .appendUint24Array(1)
            .appendUint32Array(4)
            .appendUint32Array(0xFFFFFFFFL)
            .appendUint32Array(1)
            .appendUint32Array(2)
            .appendUint64Array(8)
            .appendUint64Array(0x7FFF_FFFF_FFFF_FFFFL)
            .appendUint64Array(2)
            .appendUint64Array(3)
            .appendUint64Array(4)
            .appendUint64Array(5)
            .appendUint64Array(6)
            .appendUint64Array(7)
            .anchor("anchor")
            .appendInt8Array((byte) 127)
            .appendInt16Array((short) 2)
            .appendInt16Array((short) 0xFFFF)
            .appendInt24Array(3)
            .appendInt24Array(-1)
            .appendInt24Array(-2)
            .appendInt32Array(4)
            .appendInt32Array(-1)
            .appendInt32Array(-2)
            .appendInt32Array(-3)
            .appendInt64Array(8)
            .appendInt64Array(-1)
            .appendInt64Array(-2)
            .appendInt64Array(-3)
            .appendInt64Array(-4)
            .appendInt64Array(-5)
            .appendInt64Array(-6)
            .appendInt64Array(-7)
            .build();

        expected.putByte(0, (byte) 0xFF); // uint8Array[1]
        expected.putShort(1, (short) 2); // uint16Array[2]
        expected.putShort(3, (short) 0xFFFF);
        putMediumInt(expected, 5, 3); // uint24Array[3]
        putMediumInt(expected, 8, 0x00FF_FFFF);
        putMediumInt(expected, 11, 1);
        expected.putInt(14, 4); // uint32Array[4]
        expected.putInt(18, 0xFFFFFFFF);
        expected.putInt(22, 1);
        expected.putInt(26, 2);
        expected.putLong(30, 8L); // uint64Array[8]
        expected.putLong(38, 0x7FFF_FFFF_FFFF_FFFFL);
        expected.putLong(46, 2L);
        expected.putLong(54, 3L);
        expected.putLong(62, 4L);
        expected.putLong(70, 5L);
        expected.putLong(78, 6L);
        expected.putLong(86, 7L);
        expected.putByte(94, (byte) 6);
        expected.putStringWithoutLengthUtf8(95, "anchor");
        expected.putByte(101, (byte) 127);
        expected.putShort(102, (short) 2); // int16Array[2]
        expected.putShort(104, (short) 0xFFFF);
        putMediumInt(expected, 106, 3); // int24Array[3]
        putMediumInt(expected, 109, -1);
        putMediumInt(expected, 112, -2);
        expected.putInt(115, 4); // int32Array[4]
        expected.putInt(119, -1);
        expected.putInt(123, -2);
        expected.putInt(127, -3);
        expected.putLong(131, 8L); // int64Array[8]
        expected.putLong(139, -1L);
        expected.putLong(147, -2L);
        expected.putLong(155, -3L);
        expected.putLong(163, -4L);
        expected.putLong(171, -5L);
        expected.putLong(179, -6L);
        expected.putLong(187, -7L);

        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetAllValuesUsingIterators() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .uint8Array(IntStream.of(0xFF).iterator())
            .uint16Array(IntStream.of(2, 0xFFFF).iterator())
            .uint24Array(IntStream.of(3, 0x00FF_FFFF, 1).iterator())
            .uint32Array(LongStream.of(4L, 0xFFFFFFFFL, 1L, 2L).iterator())
            .uint64Array(LongStream.of(8L, 0x7FFF_FFFF_FFFF_FFFFL, 2L, 3L, 4L, 5L, 6L, 7L).iterator())
            .anchor("anchor")
            .int8Array(IntStream.of(127).iterator())
            .int16Array(IntStream.of(2, 0xFFFF).iterator())
            .int24Array(IntStream.of(3, -1, -2).iterator())
            .int32Array(IntStream.of(4, -1, -2, -3).iterator())
            .int64Array(LongStream.of(8L, -1L, -2L, -3L, -4L, -5L, -6L, -7L).iterator())
            .build();

        expected.putByte(0, (byte) 0xFF); // uint8Array[1]
        expected.putShort(1, (short) 2); // uint16Array[2]
        expected.putShort(3, (short) 0xFFFF);
        putMediumInt(expected, 5, 3); // uint24Array[3]
        putMediumInt(expected, 8, 0x00FF_FFFF);
        putMediumInt(expected, 11, 1);
        expected.putInt(14, 4); // uint32Array[4]
        expected.putInt(18, 0xFFFFFFFF);
        expected.putInt(22, 1);
        expected.putInt(26, 2);
        expected.putLong(30, 8L); // uint64Array[8]
        expected.putLong(38, 0x7FFF_FFFF_FFFF_FFFFL);
        expected.putLong(46, 2L);
        expected.putLong(54, 3L);
        expected.putLong(62, 4L);
        expected.putLong(70, 5L);
        expected.putLong(78, 6L);
        expected.putLong(86, 7L);
        expected.putByte(94, (byte) 6);
        expected.putStringWithoutLengthUtf8(95, "anchor");
        expected.putByte(101, (byte) 127);
        expected.putShort(102, (short) 2); // int16Array[2]
        expected.putShort(104, (short) 0xFFFF);
        putMediumInt(expected, 106, 3); // int24Array[3]
        putMediumInt(expected, 109, -1);
        putMediumInt(expected, 112, -2);
        expected.putInt(115, 4); // int32Array[4]
        expected.putInt(119, -1);
        expected.putInt(123, -2);
        expected.putInt(127, -3);
        expected.putLong(131, 8L); // int64Array[8]
        expected.putLong(139, -1L);
        expected.putLong(147, -2L);
        expected.putLong(155, -3L);
        expected.putLong(163, -4L);
        expected.putLong(171, -5L);
        expected.putLong(179, -6L);
        expected.putLong(187, -7L);

        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldConvertToString() throws Exception
    {
        final IntegerFixedArraysFW flyweight = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .appendUint8Array(0xFF)
            .appendUint16Array(2)
            .appendUint16Array(0xFFFF)
            .appendUint24Array(3)
            .appendUint24Array(1)
            .appendUint24Array(0xFFFFFF)
            .appendUint32Array(4)
            .appendUint32Array(1)
            .appendUint32Array(2)
            .appendUint32Array(0xFFFFFFFFL)
            .appendUint64Array(8)
            .appendUint64Array(21)
            .appendUint64Array(22)
            .appendUint64Array(23)
            .appendUint64Array(24)
            .appendUint64Array(25)
            .appendUint64Array(26)
            .appendUint64Array(27)
            .anchor("anchor")
            .appendInt8Array((byte) 127)
            .appendInt16Array((short) 2)
            .appendInt16Array((short) 0xFFFF)
            .appendInt24Array(3)
            .appendInt24Array(-1)
            .appendInt24Array(0xFFFFFF)
            .appendInt32Array(4)
            .appendInt32Array(-1)
            .appendInt32Array(-2)
            .appendInt32Array(-3)
            .appendInt64Array(8)
            .appendInt64Array(-1)
            .appendInt64Array(-2)
            .appendInt64Array(-3)
            .appendInt64Array(-4)
            .appendInt64Array(-5)
            .appendInt64Array(-6)
            .appendInt64Array(-7)
            .build();

        assertTrue(flyweight.toString().contains("uint16Array=[2, 65535]"));
        assertTrue(flyweight.toString().contains("int16Array=[2, -1]"));
        assertTrue(flyweight.toString().contains("uint24Array=[3, 1, 16777215]"));
        assertTrue(flyweight.toString().contains("int24Array=[3, -1, -1]"));
    }
}
