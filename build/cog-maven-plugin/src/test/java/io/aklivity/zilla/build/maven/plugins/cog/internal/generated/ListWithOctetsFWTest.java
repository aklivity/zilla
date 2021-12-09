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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generated;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.OctetsFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.Varint32FW;

public class ListWithOctetsFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final ListWithOctetsFW.Builder listWithOctetsRW = new ListWithOctetsFW.Builder();
    private final ListWithOctetsFW listWithOctetsRO = new ListWithOctetsFW();
    private final int physicalLengthSize = Byte.BYTES;
    private final int logicalLengthSize = Byte.BYTES;
    private final int bitmaskSize = Long.BYTES;

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        byte physicalLength = 28;
        byte logicalLength = 2;
        long bitmask = 18L;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);
        int offsetOctets1 = offsetBitMask + bitmaskSize;
        OctetsFW octets1 = asOctetsFW("1234567891");
        buffer.putBytes(offsetOctets1, octets1.buffer(), 0, octets1.sizeof());
        int offsetString1 = offsetOctets1 + octets1.sizeof();
        String8FW string1 = asString8FW("string1");
        buffer.putBytes(offsetString1, string1.buffer(), 0, string1.sizeof());

        for (int maxLimit = 10; maxLimit <= physicalLength; maxLimit++)
        {
            try
            {
                listWithOctetsRO.wrap(buffer,  10, maxLimit);
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
    public void shouldNotTryWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        byte physicalLength = 28;
        byte logicalLength = 2;
        long bitmask = 18L;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);
        int offsetOctets1 = offsetBitMask + bitmaskSize;
        OctetsFW octets1 = asOctetsFW("1234567891");
        buffer.putBytes(offsetOctets1, octets1.buffer(), 0, octets1.sizeof());
        int offsetString1 = offsetOctets1 + octets1.sizeof();
        String8FW string1 = asString8FW("string1");
        buffer.putBytes(offsetString1, string1.buffer(), 0, string1.sizeof());

        for (int maxLimit = 10; maxLimit <= physicalLength; maxLimit++)
        {
            assertNull(listWithOctetsRO.tryWrap(buffer,  offsetPhysicalLength, maxLimit));
        }
    }


    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        byte physicalLength = 28;
        byte logicalLength = 2;
        long bitmask = 18L;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);
        int offsetOctets1 = offsetBitMask + bitmaskSize;
        OctetsFW octets1 = asOctetsFW("1234567891");
        buffer.putBytes(offsetOctets1, octets1.buffer(), 0, octets1.sizeof());
        int offsetString1 = offsetOctets1 + octets1.sizeof();
        String8FW string1 = asString8FW("string1");
        buffer.putBytes(offsetString1, string1.buffer(), 0, string1.sizeof());
        assertSame(listWithOctetsRO, listWithOctetsRO.wrap(buffer, offsetPhysicalLength,
            offsetPhysicalLength + physicalLength));

    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        byte physicalLength = 28;
        byte logicalLength = 2;
        long bitmask = 18L;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);
        int offsetOctets1 = offsetBitMask + bitmaskSize;
        OctetsFW octets1 = asOctetsFW("1234567891");
        buffer.putBytes(offsetOctets1, octets1.buffer(), 0, octets1.sizeof());
        int offsetString1 = offsetOctets1 + octets1.sizeof();
        String8FW string1 = asString8FW("string1");
        buffer.putBytes(offsetString1, string1.buffer(), 0, string1.sizeof());
        assertSame(listWithOctetsRO, listWithOctetsRO.tryWrap(buffer, offsetPhysicalLength,
            offsetPhysicalLength + physicalLength));
    }

    @Test
    public void shouldWrapOnlyRequiredFieldsAndRetrieveFieldsWithDefaultValues()
    {
        byte physicalLength = 28;
        byte logicalLength = 2;
        long bitmask = 18L;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);
        int offsetOctets1 = offsetBitMask + bitmaskSize;
        OctetsFW octets1 = asOctetsFW("1234567891");
        buffer.putBytes(offsetOctets1, octets1.buffer(), 0, octets1.sizeof());
        int offsetString1 = offsetOctets1 + octets1.sizeof();
        String8FW string1 = asString8FW("string1");
        buffer.putBytes(offsetString1, string1.buffer(), 0, string1.sizeof());
        assertSame(listWithOctetsRO, listWithOctetsRO.wrap(buffer, offsetPhysicalLength,
            offsetPhysicalLength + physicalLength));
        assertEquals(11L, listWithOctetsRO.fixed1());
        assertEquals(-1, listWithOctetsRO.lengthOctets3());
        assertNull(listWithOctetsRO.octets3());
        assertEquals(-1, listWithOctetsRO.lengthOctets4());
        assertNull(listWithOctetsRO.octets4());
    }

    @Test
    public void shouldWrapAllFields()
    {
        byte physicalLength = 62;
        byte logicalLength = 10;
        long bitmask = 0x03FF;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);
        int offsetFixed1 = offsetBitMask + bitmaskSize;
        buffer.putInt(offsetFixed1, 12345);
        int offsetOctets1 = offsetFixed1 + Integer.BYTES;
        OctetsFW octets1 = asOctetsFW("1234567891");
        buffer.putBytes(offsetOctets1, octets1.buffer(), 0, octets1.sizeof());
        int offsetLengthOctets2 = offsetOctets1 + octets1.sizeof();
        buffer.putShort(offsetLengthOctets2, (short) 15);
        int offsetOctets2 = offsetLengthOctets2 + Short.BYTES;
        OctetsFW octets2 = asOctetsFW("123456789123456");
        buffer.putBytes(offsetOctets2, octets2.buffer(), 0, octets2.sizeof());
        int offsetString1 = offsetOctets2 + octets2.sizeof();
        String8FW string1 = asString8FW("string1");
        buffer.putBytes(offsetString1, string1.buffer(), 0, string1.sizeof());
        int offsetLengthOctets3 = offsetString1 + string1.sizeof();
        Varint32FW lengthOctets3 = asVarint32FW(5);
        buffer.putBytes(offsetLengthOctets3, lengthOctets3.buffer(), 0, lengthOctets3.sizeof());
        int offsetOctets3 = offsetLengthOctets3 + lengthOctets3.sizeof();
        OctetsFW octets3 = asOctetsFW("12345");
        buffer.putBytes(offsetOctets3, octets3.buffer(), 0, octets3.sizeof());
        int offsetLengthOctets4 = offsetOctets3 + octets3.sizeof();
        buffer.putInt(offsetLengthOctets4, 3);
        int offsetOctets4 = offsetLengthOctets4 + Integer.BYTES;
        OctetsFW octets4 = asOctetsFW("123");
        buffer.putBytes(offsetOctets4, octets4.buffer(), 0, octets4.sizeof());

        assertSame(listWithOctetsRO, listWithOctetsRO.wrap(buffer, offsetPhysicalLength,
            offsetPhysicalLength + physicalLength));
        assertEquals(physicalLength, listWithOctetsRO.limit() - offsetPhysicalLength);
        assertEquals(logicalLength, listWithOctetsRO.length());
        assertEquals(12345, listWithOctetsRO.fixed1());
        assertEquals("octets[10]", listWithOctetsRO.octets1().toString());
        assertEquals(15, listWithOctetsRO.lengthOctets2());
        assertEquals("octets[15]", listWithOctetsRO.octets2().toString());
        assertEquals("string1", listWithOctetsRO.string1().asString());
        assertEquals(5, listWithOctetsRO.lengthOctets3());
        assertEquals("octets[5]", listWithOctetsRO.octets3().toString());
        assertEquals(3, listWithOctetsRO.lengthOctets4());
        assertEquals("octets[3]", listWithOctetsRO.octets4().toString());
    }

    @Test
    public void shouldDefaultValues() throws Exception
    {
        int limit = listWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets2(b -> b.put("12345678901".getBytes(UTF_8)))
            .string1("value1")
            .build()
            .limit();
        listWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(11, listWithOctetsRO.fixed1());
        assertNull(listWithOctetsRO.octets3());
    }

    @Test
    public void shouldExplicitlySetOctetsValuesWithNullDefaultToNull() throws Exception
    {
        int limit = listWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .octets1(asOctetsFW("1234567890"))
            .octets2(asOctetsFW("12345678901"))
            .string1("value1")
            .octets3((OctetsFW) null)
            .octets4((OctetsFW) null)
            .build()
            .limit();
        listWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(11, listWithOctetsRO.fixed1());
        assertNull(listWithOctetsRO.octets3());
        assertNull(listWithOctetsRO.octets4());
    }

    @Test
    public void shouldAutomaticallySetLength() throws Exception
    {
        int limit = listWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets2(b -> b.put("123456".getBytes(UTF_8)))
            .string1("value1")
            .build()
            .limit();
        listWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, listWithOctetsRO.lengthOctets2());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetFixed1WithInsufficientSpace()
    {
        listWithOctetsRW.wrap(buffer, 10, 11)
            .fixed1(10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpaceToDefaultPriorField()
    {
        listWithOctetsRW.wrap(buffer, 10, 11)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1("");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpace()
    {
        listWithOctetsRW.wrap(buffer, 10, 18)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1("1234");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetOctets1WithInsufficientSpace()
    {
        listWithOctetsRW.wrap(buffer, 10, 16)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetOctets1WithValueLongerThanSize()
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("12345678901".getBytes(UTF_8)));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToSetOctets1WithValueShorterThanSize()
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("123456789".getBytes(UTF_8)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetOctets1WithValueLongerThanSizeUsingBuffer()
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(asBuffer("12345678901"), 0, 11);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetOctets1WithValueShorterThanSizeUsingBuffer()
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(0)
            .octets1(asBuffer("123456789"), 0, 9);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetFixed1() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .fixed1(101)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets1UsingOctetsFW() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(asOctetsFW("1234567890"))
            .octets1(asOctetsFW("01234"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets1UsingConsumer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets1(b -> b.put("01234".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets1UsingDirectBuffer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(asBuffer("1234567890"), 0, "1234567890".length())
            .octets1(asBuffer("01234"), 0, "01234".length())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets2UsingOctetsFW() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(asOctetsFW("1234567890"))
            .octets2(asOctetsFW("1234567890"))
            .octets2(asOctetsFW("01234"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets2UsingConsumer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets2(b -> b.put("01234".getBytes(UTF_8)))
            .octets2(b -> b.put("1234567890".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets2UsingDirectBuffer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(asBuffer("1234567890"), 0, "1234567890".length())
            .octets2(asBuffer("01234"), 0, "01234".length())
            .octets2(asBuffer("1234567890"), 0, "1234567890".length())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetString1() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1("value1")
            .string1("another value")
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetString1UsingString8FW() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1(asString8FW("value1"))
            .string1(asString8FW("another value"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetString1UsingDirectBuffer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1(asBuffer("value1"), 0, "value1".length())
            .string1(asBuffer("another value"), 0, "another value".length())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets3UsingOctetsFW() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(asOctetsFW("1234567890"))
            .string1("value1")
            .octets3(asOctetsFW("1234567890"))
            .octets3(asOctetsFW("01234"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets3UsingConsumer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1("value1")
            .octets3(b -> b.put("01234".getBytes(UTF_8)))
            .octets3(b -> b.put("1234567890".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets3UsingDirectBuffer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(asBuffer("1234567890"), 0, "1234567890".length())
            .string1("value1")
            .octets3(asBuffer("01234"), 0, "01234".length())
            .octets3(asBuffer("1234567890"), 0, "1234567890".length())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets4UsingOctetsFW() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(asOctetsFW("1234567890"))
            .string1("value1")
            .octets4(asOctetsFW("1234567890"))
            .octets4(asOctetsFW("01234"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets4UsingConsumer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1("value1")
            .octets4(b -> b.put("01234".getBytes(UTF_8)))
            .octets4(b -> b.put("1234567890".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetOctets4UsingDirectBuffer() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(asBuffer("1234567890"), 0, "1234567890".length())
            .string1("value1")
            .octets4(asBuffer("01234"), 0, "01234".length())
            .octets4(asBuffer("1234567890"), 0, "1234567890".length())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenRequiredOctets1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenRequiredString1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets2UsingOctetsFWWhenRequiredOctets1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets2(asOctetsFW("1234567890"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets2UsingConsumeWhenRequiredOctets1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets2(b -> b.put("1234567890".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets2UsingDirectBufferWhenRequiredOctets1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets2(asBuffer("12345"), 0, "12345".length())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetString1WhenRequiredOctets1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .string1("1234567890")
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetString1UsingString8FWWhenRequiredOctets1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .string1(asString8FW("1234567890"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetString1UsingDirectBufferWhenRequiredOctets1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .string1(asBuffer("12345"), 0, "12345".length())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets3UsingOctetsFWWhenRequiredString1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets3(asOctetsFW("1234567890"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets3UsingConsumeWhenRequiredString1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets3(b -> b.put("1234567890".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets3UsingDirectBufferWhenRequiredString1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets3(asBuffer("12345"), 0, "12345".length())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets4UsingOctetsFWWhenRequiredString1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets4(asOctetsFW("1234567890"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets4UsingConsumeWhenRequiredString1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets4(b -> b.put("1234567890".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetOctets4UsingDirectBufferWhenRequiredString1NotSet() throws Exception
    {
        listWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets4(asBuffer("12345"), 0, "12345".length())
            .build();
    }

    @Test
    public void shouldSetAllValuesUsingOctetsFW() throws Exception
    {
        int limit = listWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(5)
            .octets1(asOctetsFW("1234567890"))
            .octets2(asOctetsFW("12345"))
            .string1("value1")
            .octets3(asOctetsFW("678"))
            .octets4(asOctetsFW("987654"))
            .build()
            .limit();
        listWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(5, listWithOctetsRO.fixed1());
        final String octets1 = listWithOctetsRO.octets1().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("1234567890", octets1);
        assertEquals(5, listWithOctetsRO.lengthOctets2());
        final String octets2 = listWithOctetsRO.octets2().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("12345", octets2);
        assertEquals("value1", listWithOctetsRO.string1().asString());
        assertEquals(3, listWithOctetsRO.lengthOctets3());
        final String octets3 = listWithOctetsRO.octets3().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("678", octets3);
        assertEquals(6, listWithOctetsRO.lengthOctets4());
        final String octets4 = listWithOctetsRO.octets4().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("987654", octets4);
    }

    @Test
    public void shouldSetAllValuesUsingConsumer() throws Exception
    {
        int limit = listWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(5)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets2(b -> b.put("12345".getBytes(UTF_8)))
            .string1("value1")
            .octets3(b -> b.put("678".getBytes(UTF_8)))
            .octets4(b -> b.put("987654".getBytes(UTF_8)))
            .build()
            .limit();
        listWithOctetsRO.wrap(buffer, 0, limit);
        assertEquals(5, listWithOctetsRO.fixed1());
        final String octets1 = listWithOctetsRO.octets1().get(
            (buffer, offset, limit2) -> buffer.getStringWithoutLengthUtf8(offset, limit2 - offset));
        assertEquals("1234567890", octets1);
        assertEquals(5, listWithOctetsRO.lengthOctets2());
        final String octets2 = listWithOctetsRO.octets2().get(
            (buffer, offset, limit2) -> buffer.getStringWithoutLengthUtf8(offset, limit2 - offset));
        assertEquals("12345", octets2);
        assertEquals("value1", listWithOctetsRO.string1().asString());
        assertEquals(3, listWithOctetsRO.lengthOctets3());
        final String octets3 = listWithOctetsRO.octets3().get(
            (buffer, offset, limit2) -> buffer.getStringWithoutLengthUtf8(offset, limit2 - offset));
        assertEquals("678", octets3);
        assertEquals(6, listWithOctetsRO.lengthOctets4());
        final String octets4 = listWithOctetsRO.octets4().get(
            (buffer, offset, limit2) -> buffer.getStringWithoutLengthUtf8(offset, limit2 - offset));
        assertEquals("987654", octets4);
    }

    @Test
    public void shouldSetAllValuesUsingDirectBuffer() throws Exception
    {
        int limit = listWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(5)
            .octets1(asBuffer("1234567890"), 0, "1234567890".length())
            .octets2(asBuffer("12345"), 0, "12345".length())
            .string1(asBuffer("value1"), 0, "value1".length())
            .octets3(asBuffer("678"), 0, "678".length())
            .octets4(asBuffer("987654"), 0, "987654".length())
            .build()
            .limit();
        listWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(5, listWithOctetsRO.fixed1());
        final String octets1 = listWithOctetsRO.octets1().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("1234567890", octets1);
        assertEquals(5, listWithOctetsRO.lengthOctets2());
        final String octets2 = listWithOctetsRO.octets2().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("12345", octets2);
        assertEquals("value1", listWithOctetsRO.string1().asString());
        assertEquals(3, listWithOctetsRO.lengthOctets3());
        final String octets3 = listWithOctetsRO.octets3().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("678", octets3);
        assertEquals(6, listWithOctetsRO.lengthOctets4());
        final String octets4 = listWithOctetsRO.octets4().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("987654", octets4);
    }

    @Test
    public void shouldSetStringValuesUsingString8FW() throws Exception
    {
        int limit = listWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(5)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .octets2(b -> b.put("12345".getBytes(UTF_8)))
            .string1(asString8FW("value1"))
            .build()
            .limit();
        listWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(5, listWithOctetsRO.fixed1());
        assertEquals("value1", listWithOctetsRO.string1().asString());
    }

    private static DirectBuffer asBuffer(
        String value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(value.length()));
        valueBuffer.putStringWithoutLengthUtf8(0, value);
        return valueBuffer;
    }

    private static OctetsFW asOctetsFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new OctetsFW.Builder().wrap(buffer, 0, buffer.capacity()).set(value.getBytes(UTF_8)).build();
    }

    private static String8FW asString8FW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }

    private static Varint32FW asVarint32FW(
        int value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Integer.BYTES));
        return new Varint32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value).build();
    }
}
