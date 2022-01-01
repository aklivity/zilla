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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt64;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt64FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithString;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithStringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint16;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint16FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint32;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.ListWithEnumFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.Roll;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.RollFW;

public class ListWithEnumFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final ListWithEnumFW.Builder listWithEnumRW = new ListWithEnumFW.Builder();
    private final ListWithEnumFW listWithEnumRO = new ListWithEnumFW();
    private final int physicalLengthSize = Byte.BYTES;
    private final int logicalLengthSize = Byte.BYTES;
    private final int bitmaskSize = Long.BYTES;

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        byte physicalLength = 37;
        byte logicalLength = 6;
        long bitmask = 0x3F;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetRoll = offsetBitMask + bitmaskSize;
        RollFW roll = asRollFW(Roll.EGG);
        buffer.putBytes(offsetRoll, roll.buffer(), 0, roll.sizeof());

        int offsetEnumWithInt8 = offsetRoll + roll.sizeof();
        EnumWithInt8FW enumWithInt8 = asEnumWithInt8FW(EnumWithInt8.THREE);
        buffer.putBytes(offsetEnumWithInt8, enumWithInt8.buffer(), 0, enumWithInt8.sizeof());

        int offsetEnumWithInt64 = offsetEnumWithInt8 + enumWithInt8.sizeof();
        EnumWithInt64FW enumWithInt64 = asEnumWithInt64FW(EnumWithInt64.ELEVEN);
        buffer.putBytes(offsetEnumWithInt64, enumWithInt64.buffer(), 0, enumWithInt64.sizeof());

        int offsetEnumWithUint16 = offsetEnumWithInt64 + enumWithInt64.sizeof();
        EnumWithUint16FW enumWithUint16 = asEnumWithUint16FW(EnumWithUint16.ICHI);
        buffer.putBytes(offsetEnumWithUint16, enumWithUint16.buffer(), 0, enumWithUint16.sizeof());

        int offsetEnumWithUint32 = offsetEnumWithUint16 + enumWithUint16.sizeof();
        EnumWithUint32FW enumWithUint32 = asEnumWithUint32FW(EnumWithUint32.NI);
        buffer.putBytes(offsetEnumWithUint32, enumWithUint32.buffer(), 0, enumWithUint32.sizeof());

        int offsetEnumWithString = offsetEnumWithUint32 + enumWithUint32.sizeof();
        EnumWithStringFW enumWithString = asEnumWithStringFW(EnumWithString.BLUE);
        buffer.putBytes(offsetEnumWithString, enumWithString.buffer(), 0, enumWithString.sizeof());

        for (int maxLimit = 10; maxLimit <= physicalLength; maxLimit++)
        {
            try
            {
                listWithEnumRO.wrap(buffer,  10, maxLimit);
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
        byte physicalLength = 37;
        byte logicalLength = 6;
        long bitmask = 0x3F;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetRoll = offsetBitMask + bitmaskSize;
        RollFW roll = asRollFW(Roll.EGG);
        buffer.putBytes(offsetRoll, roll.buffer(), 0, roll.sizeof());

        int offsetEnumWithInt8 = offsetRoll + roll.sizeof();
        EnumWithInt8FW enumWithInt8 = asEnumWithInt8FW(EnumWithInt8.THREE);
        buffer.putBytes(offsetEnumWithInt8, enumWithInt8.buffer(), 0, enumWithInt8.sizeof());

        int offsetEnumWithInt64 = offsetEnumWithInt8 + enumWithInt8.sizeof();
        EnumWithInt64FW enumWithInt64 = asEnumWithInt64FW(EnumWithInt64.ELEVEN);
        buffer.putBytes(offsetEnumWithInt64, enumWithInt64.buffer(), 0, enumWithInt64.sizeof());

        int offsetEnumWithUint16 = offsetEnumWithInt64 + enumWithInt64.sizeof();
        EnumWithUint16FW enumWithUint16 = asEnumWithUint16FW(EnumWithUint16.ICHI);
        buffer.putBytes(offsetEnumWithUint16, enumWithUint16.buffer(), 0, enumWithUint16.sizeof());

        int offsetEnumWithUint32 = offsetEnumWithUint16 + enumWithUint16.sizeof();
        EnumWithUint32FW enumWithUint32 = asEnumWithUint32FW(EnumWithUint32.NI);
        buffer.putBytes(offsetEnumWithUint32, enumWithUint32.buffer(), 0, enumWithUint32.sizeof());

        int offsetEnumWithString = offsetEnumWithUint32 + enumWithUint32.sizeof();
        EnumWithStringFW enumWithString = asEnumWithStringFW(EnumWithString.BLUE);
        buffer.putBytes(offsetEnumWithString, enumWithString.buffer(), 0, enumWithString.sizeof());

        for (int maxLimit = 10; maxLimit <= physicalLength; maxLimit++)
        {
            assertNull(listWithEnumRO.tryWrap(buffer,  offsetPhysicalLength, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        byte physicalLength = 37;
        byte logicalLength = 6;
        long bitmask = 0x3F;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetRoll = offsetBitMask + bitmaskSize;
        RollFW roll = asRollFW(Roll.EGG);
        buffer.putBytes(offsetRoll, roll.buffer(), 0, roll.sizeof());

        int offsetEnumWithInt8 = offsetRoll + roll.sizeof();
        EnumWithInt8FW enumWithInt8 = asEnumWithInt8FW(EnumWithInt8.THREE);
        buffer.putBytes(offsetEnumWithInt8, enumWithInt8.buffer(), 0, enumWithInt8.sizeof());

        int offsetEnumWithInt64 = offsetEnumWithInt8 + enumWithInt8.sizeof();
        EnumWithInt64FW enumWithInt64 = asEnumWithInt64FW(EnumWithInt64.ELEVEN);
        buffer.putBytes(offsetEnumWithInt64, enumWithInt64.buffer(), 0, enumWithInt64.sizeof());

        int offsetEnumWithUint16 = offsetEnumWithInt64 + enumWithInt64.sizeof();
        EnumWithUint16FW enumWithUint16 = asEnumWithUint16FW(EnumWithUint16.ICHI);
        buffer.putBytes(offsetEnumWithUint16, enumWithUint16.buffer(), 0, enumWithUint16.sizeof());

        int offsetEnumWithUint32 = offsetEnumWithUint16 + enumWithUint16.sizeof();
        EnumWithUint32FW enumWithUint32 = asEnumWithUint32FW(EnumWithUint32.NI);
        buffer.putBytes(offsetEnumWithUint32, enumWithUint32.buffer(), 0, enumWithUint32.sizeof());

        int offsetEnumWithString = offsetEnumWithUint32 + enumWithUint32.sizeof();
        EnumWithStringFW enumWithString = asEnumWithStringFW(EnumWithString.BLUE);
        buffer.putBytes(offsetEnumWithString, enumWithString.buffer(), 0, enumWithString.sizeof());

        assertSame(listWithEnumRO, listWithEnumRO.wrap(buffer, offsetPhysicalLength,
            offsetPhysicalLength + physicalLength));
        assertEquals(physicalLength, listWithEnumRO.limit() - offsetPhysicalLength);
        assertEquals(logicalLength, listWithEnumRO.fieldCount());
        assertEquals(Roll.EGG, listWithEnumRO.roll());
        assertEquals(EnumWithInt8.THREE, listWithEnumRO.enumWithInt8());
        assertEquals(EnumWithInt64.ELEVEN, listWithEnumRO.enumWithInt64());
        assertEquals(EnumWithUint16.ICHI, listWithEnumRO.enumWithUint16());
        assertEquals(EnumWithUint32.NI, listWithEnumRO.enumWithUint32());
        assertEquals(EnumWithString.BLUE, listWithEnumRO.enumWithString());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        byte physicalLength = 37;
        byte logicalLength = 6;
        long bitmask = 0x3F;
        int offsetPhysicalLength = 10;
        buffer.putByte(offsetPhysicalLength, physicalLength);
        int offsetLogicalLength = offsetPhysicalLength + physicalLengthSize;
        buffer.putByte(offsetLogicalLength, logicalLength);
        int offsetBitMask = offsetLogicalLength + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetRoll = offsetBitMask + bitmaskSize;
        RollFW roll = asRollFW(Roll.EGG);
        buffer.putBytes(offsetRoll, roll.buffer(), 0, roll.sizeof());

        int offsetEnumWithInt8 = offsetRoll + roll.sizeof();
        EnumWithInt8FW enumWithInt8 = asEnumWithInt8FW(EnumWithInt8.THREE);
        buffer.putBytes(offsetEnumWithInt8, enumWithInt8.buffer(), 0, enumWithInt8.sizeof());

        int offsetEnumWithInt64 = offsetEnumWithInt8 + enumWithInt8.sizeof();
        EnumWithInt64FW enumWithInt64 = asEnumWithInt64FW(EnumWithInt64.ELEVEN);
        buffer.putBytes(offsetEnumWithInt64, enumWithInt64.buffer(), 0, enumWithInt64.sizeof());

        int offsetEnumWithUint16 = offsetEnumWithInt64 + enumWithInt64.sizeof();
        EnumWithUint16FW enumWithUint16 = asEnumWithUint16FW(EnumWithUint16.ICHI);
        buffer.putBytes(offsetEnumWithUint16, enumWithUint16.buffer(), 0, enumWithUint16.sizeof());

        int offsetEnumWithUint32 = offsetEnumWithUint16 + enumWithUint16.sizeof();
        EnumWithUint32FW enumWithUint32 = asEnumWithUint32FW(EnumWithUint32.NI);
        buffer.putBytes(offsetEnumWithUint32, enumWithUint32.buffer(), 0, enumWithUint32.sizeof());

        int offsetEnumWithString = offsetEnumWithUint32 + enumWithUint32.sizeof();
        EnumWithStringFW enumWithString = asEnumWithStringFW(EnumWithString.BLUE);
        buffer.putBytes(offsetEnumWithString, enumWithString.buffer(), 0, enumWithString.sizeof());

        assertSame(listWithEnumRO, listWithEnumRO.tryWrap(buffer, offsetPhysicalLength,
            offsetPhysicalLength + physicalLength));
        assertEquals(physicalLength, listWithEnumRO.limit() - offsetPhysicalLength);
        assertEquals(logicalLength, listWithEnumRO.fieldCount());
        assertEquals(Roll.EGG, listWithEnumRO.roll());
        assertEquals(EnumWithInt8.THREE, listWithEnumRO.enumWithInt8());
        assertEquals(EnumWithInt64.ELEVEN, listWithEnumRO.enumWithInt64());
        assertEquals(EnumWithUint16.ICHI, listWithEnumRO.enumWithUint16());
        assertEquals(EnumWithUint32.NI, listWithEnumRO.enumWithUint32());
        assertEquals(EnumWithString.BLUE, listWithEnumRO.enumWithString());
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetFieldWhenSubsequentFieldAlreadySet() throws Exception
    {
        listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .roll(asRollFW(Roll.EGG))
            .enumWithInt64(EnumWithInt64.ELEVEN)
            .enumWithInt8(EnumWithInt8.THREE)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetSameFieldMoreThanOnce() throws Exception
    {
        listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .roll(asRollFW(Roll.EGG))
            .roll(asRollFW(Roll.FORWARD))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWithoutSettingRequiredField() throws Exception
    {
        listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetFieldBeforeSettingPriorRequiredField() throws Exception
    {
        listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .enumWithInt8(asEnumWithInt8FW(EnumWithInt8.THREE))
            .enumWithUint16(asEnumWithUint16FW(EnumWithUint16.ICHI))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToAccessFieldsNotSet() throws Exception
    {
        int limit = listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .roll(asRollFW(Roll.EGG))
            .enumWithUint16(asEnumWithUint16FW(EnumWithUint16.ICHI))
            .build()
            .limit();
        listWithEnumRO.wrap(buffer, 0, limit);
        listWithEnumRO.enumWithInt8();
    }

    @Test
    public void shouldGetFieldsWithDefaultWhenNotSet() throws Exception
    {
        int limit = listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .roll(asRollFW(Roll.EGG))
            .enumWithUint16(asEnumWithUint16FW(EnumWithUint16.ICHI))
            .build()
            .limit();
        listWithEnumRO.wrap(buffer, 0, limit);
        assertEquals(Roll.EGG, listWithEnumRO.roll());
        assertEquals(EnumWithInt64.TEN, listWithEnumRO.enumWithInt64());
        assertEquals(EnumWithUint16.ICHI, listWithEnumRO.enumWithUint16());
        assertEquals(EnumWithUint32.SAN, listWithEnumRO.enumWithUint32());
    }

    @Test
    public void shouldSetOnlyRequiredFieldsUsingEnumFW() throws Exception
    {
        int limit = listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .roll(asRollFW(Roll.EGG))
            .enumWithUint16(asEnumWithUint16FW(EnumWithUint16.ICHI))
            .build()
            .limit();
        listWithEnumRO.wrap(buffer, 0, limit);
        assertEquals(Roll.EGG, listWithEnumRO.roll());
        assertEquals(EnumWithUint16.ICHI, listWithEnumRO.enumWithUint16());
    }

    @Test
    public void shouldSetOnlyRequiredFieldsUsingEnum() throws Exception
    {
        int limit = listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .roll(Roll.EGG)
            .enumWithUint16(EnumWithUint16.ICHI)
            .build()
            .limit();
        listWithEnumRO.wrap(buffer, 0, limit);
        assertEquals(Roll.EGG, listWithEnumRO.roll());
        assertEquals(EnumWithUint16.ICHI, listWithEnumRO.enumWithUint16());
        assertEquals(EnumWithUint32.SAN, listWithEnumRO.enumWithUint32());
    }

    @Test
    public void shouldSetAllValuesUsingEnumFW() throws Exception
    {
        int limit = listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .roll(asRollFW(Roll.EGG))
            .enumWithInt8(asEnumWithInt8FW(EnumWithInt8.THREE))
            .enumWithInt64(asEnumWithInt64FW(EnumWithInt64.ELEVEN))
            .enumWithUint16(asEnumWithUint16FW(EnumWithUint16.ICHI))
            .enumWithUint32(asEnumWithUint32FW(EnumWithUint32.NI))
            .enumWithString(asEnumWithStringFW(EnumWithString.YELLOW))
            .build()
            .limit();
        listWithEnumRO.wrap(buffer, 0, limit);
        assertEquals(Roll.EGG, listWithEnumRO.roll());
        assertEquals(EnumWithInt8.THREE, listWithEnumRO.enumWithInt8());
        assertEquals(EnumWithInt64.ELEVEN, listWithEnumRO.enumWithInt64());
        assertEquals(EnumWithUint16.ICHI, listWithEnumRO.enumWithUint16());
        assertEquals(EnumWithUint32.NI, listWithEnumRO.enumWithUint32());
        assertEquals(EnumWithString.YELLOW, listWithEnumRO.enumWithString());
    }

    @Test
    public void shouldSetAllValuesUsingEnum() throws Exception
    {
        int limit = listWithEnumRW.wrap(buffer, 0, buffer.capacity())
            .roll(Roll.EGG)
            .enumWithInt8(EnumWithInt8.THREE)
            .enumWithInt64(EnumWithInt64.ELEVEN)
            .enumWithUint16(EnumWithUint16.ICHI)
            .enumWithUint32(EnumWithUint32.NI)
            .enumWithString(EnumWithString.YELLOW)
            .build()
            .limit();
        listWithEnumRO.wrap(buffer, 0, limit);
        assertEquals(Roll.EGG, listWithEnumRO.roll());
        assertEquals(EnumWithInt8.THREE, listWithEnumRO.enumWithInt8());
        assertEquals(EnumWithInt64.ELEVEN, listWithEnumRO.enumWithInt64());
        assertEquals(EnumWithUint16.ICHI, listWithEnumRO.enumWithUint16());
        assertEquals(EnumWithUint32.NI, listWithEnumRO.enumWithUint32());
        assertEquals(EnumWithString.YELLOW, listWithEnumRO.enumWithString());
    }

    private static RollFW asRollFW(
        Roll value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES));
        return new RollFW.Builder().wrap(buffer, 0, buffer.capacity()).set(value).build();
    }

    private static EnumWithInt8FW asEnumWithInt8FW(
        EnumWithInt8 value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES));
        return new EnumWithInt8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value).build();
    }

    private static EnumWithInt64FW asEnumWithInt64FW(
        EnumWithInt64 value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Long.BYTES));
        return new EnumWithInt64FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value).build();
    }

    private static EnumWithUint16FW asEnumWithUint16FW(
        EnumWithUint16 value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Integer.BYTES));
        return new EnumWithUint16FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value).build();
    }

    private static EnumWithUint32FW asEnumWithUint32FW(
        EnumWithUint32 value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Long.BYTES));
        return new EnumWithUint32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value).build();
    }

    private static EnumWithStringFW asEnumWithStringFW(
        EnumWithString value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + value.value().length()));
        return new EnumWithStringFW.Builder().wrap(buffer, 0, buffer.capacity())
            .set(value, StandardCharsets.UTF_8).build();
    }
}
