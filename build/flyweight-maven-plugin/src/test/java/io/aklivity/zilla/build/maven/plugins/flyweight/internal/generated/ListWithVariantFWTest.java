/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.generated;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt16;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint16;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint32;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.ListWithVariantFW;

public class ListWithVariantFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final ListWithVariantFW.Builder listWithVariantOfIntRW = new ListWithVariantFW.Builder();
    private final ListWithVariantFW listWithVariantOfIntRO = new ListWithVariantFW();
    private final int lengthSize = Byte.BYTES;
    private final int logicalLengthSize = Byte.BYTES;
    private final int bitmaskSize = Long.BYTES;
    private static final EnumWithInt8 KIND_STRING8 = EnumWithInt8.NINE;

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        byte length = 27;
        byte logicalLength = 6;
        long bitmask = 0x3F;
        int offsetLength = 10;
        buffer.putByte(offsetLength, length);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, logicalLength);
        int offsetBitMask = offsetFieldCount + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetIntField1 = offsetBitMask + bitmaskSize;
        buffer.putByte(offsetIntField1, (byte) 1);
        int offsetvariantOfInt64 = offsetIntField1 + Byte.BYTES;
        buffer.putByte(offsetvariantOfInt64, (byte) 113);
        int offsetVariantOfInt64 = offsetvariantOfInt64 + Byte.BYTES;
        buffer.putInt(offsetVariantOfInt64, 100000);
        int offsetvariantOfInt8 = offsetVariantOfInt64 + Integer.BYTES;
        buffer.putByte(offsetvariantOfInt8, EnumWithInt8.ONE.value());
        int offsetVariantOfInt8 = offsetvariantOfInt8 + Byte.BYTES;
        buffer.putByte(offsetVariantOfInt8, (byte) 100);
        int offsetIntField2 = offsetVariantOfInt8 + Byte.BYTES;
        buffer.putShort(offsetIntField2, (short) 30000);
        int offsetvariantOfInt16 = offsetIntField2 + Short.BYTES;
        buffer.putShort(offsetvariantOfInt16, EnumWithInt16.THREE.value());
        int offsetVariantOfInt16 = offsetvariantOfInt16 + Short.BYTES;
        buffer.putShort(offsetVariantOfInt16, (short) 2000);
        int offsetvariantOfInt32 = offsetVariantOfInt16 + Short.BYTES;
        buffer.putByte(offsetvariantOfInt32, EnumWithInt8.TWO.value());
        int offsetVariantOfInt32 = offsetvariantOfInt32 + Byte.BYTES;
        buffer.putShort(offsetVariantOfInt32, (short) -500);

        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            try
            {
                listWithVariantOfIntRO.wrap(buffer,  10, maxLimit);
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
        byte length = 27;
        byte logicalLength = 6;
        long bitmask = 0x3F;
        int offsetLength = 10;
        buffer.putByte(offsetLength, length);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, logicalLength);
        int offsetBitMask = offsetFieldCount + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetIntField1 = offsetBitMask + bitmaskSize;
        buffer.putByte(offsetIntField1, (byte) 1);
        int offsetvariantOfInt64 = offsetIntField1 + Byte.BYTES;
        buffer.putByte(offsetvariantOfInt64, (byte) 113);
        int offsetVariantOfInt64 = offsetvariantOfInt64 + Byte.BYTES;
        buffer.putInt(offsetVariantOfInt64, 100000);
        int offsetvariantOfInt8 = offsetVariantOfInt64 + Integer.BYTES;
        buffer.putByte(offsetvariantOfInt8, EnumWithInt8.ONE.value());
        int offsetVariantOfInt8 = offsetvariantOfInt8 + Byte.BYTES;
        buffer.putByte(offsetVariantOfInt8, (byte) 100);
        int offsetIntField2 = offsetVariantOfInt8 + Byte.BYTES;
        buffer.putShort(offsetIntField2, (short) 30000);
        int offsetvariantOfInt16 = offsetIntField2 + Short.BYTES;
        buffer.putShort(offsetvariantOfInt16, EnumWithInt16.THREE.value());
        int offsetVariantOfInt16 = offsetvariantOfInt16 + Short.BYTES;
        buffer.putShort(offsetVariantOfInt16, (short) 2000);
        int offsetvariantOfInt32 = offsetVariantOfInt16 + Short.BYTES;
        buffer.putByte(offsetvariantOfInt32, EnumWithInt8.TWO.value());
        int offsetVariantOfInt32 = offsetvariantOfInt32 + Byte.BYTES;
        buffer.putShort(offsetVariantOfInt32, (short) -500);

        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            assertNull(listWithVariantOfIntRO.tryWrap(buffer,  offsetLength, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        byte length = 50;
        byte fieldCount = 10;
        long bitmask = 0x03FF;
        int offsetLength = 10;
        buffer.putByte(offsetLength, length);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, fieldCount);
        int offsetBitMask = offsetFieldCount + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetIntField1 = offsetBitMask + bitmaskSize;
        buffer.putByte(offsetIntField1, (byte) 1);

        int offsetKindVariantOfInt64 = offsetIntField1 + Byte.BYTES;
        buffer.putByte(offsetKindVariantOfInt64, (byte) 113);
        int offsetVariantOfInt64 = offsetKindVariantOfInt64 + Byte.BYTES;
        buffer.putInt(offsetVariantOfInt64, 100000);

        int offsetKindVariantOfInt8 = offsetVariantOfInt64 + Integer.BYTES;
        buffer.putByte(offsetKindVariantOfInt8, EnumWithInt8.ONE.value());
        int offsetVariantOfInt8 = offsetKindVariantOfInt8 + Byte.BYTES;
        buffer.putByte(offsetVariantOfInt8, (byte) 100);

        int offsetIntField2 = offsetVariantOfInt8 + Byte.BYTES;
        buffer.putShort(offsetIntField2, (short) 30000);

        int offsetKindVariantOfInt16 = offsetIntField2 + Short.BYTES;
        buffer.putShort(offsetKindVariantOfInt16, EnumWithInt16.THREE.value());
        int offsetVariantOfInt16 = offsetKindVariantOfInt16 + Short.BYTES;
        buffer.putShort(offsetVariantOfInt16, (short) 2000);

        int offsetKindVariantOfInt32 = offsetVariantOfInt16 + Short.BYTES;
        buffer.putByte(offsetKindVariantOfInt32, EnumWithInt8.TWO.value());
        int offsetVariantOfInt32 = offsetKindVariantOfInt32 + Byte.BYTES;
        buffer.putShort(offsetVariantOfInt32, (short) -500);

        int offsetKindVariantOfUint8 = offsetVariantOfInt32 + Short.BYTES;
        buffer.putByte(offsetKindVariantOfUint8, (byte) EnumWithUint8.ICHI.value());
        int offsetVariantOfUint8 = offsetKindVariantOfUint8 + Byte.BYTES;
        buffer.putByte(offsetVariantOfUint8, (byte) 200);

        int offsetKindVariantOfUint16 = offsetVariantOfUint8 + Byte.BYTES;
        buffer.putShort(offsetKindVariantOfUint16, (short) EnumWithUint16.ICHI.value());
        int offsetVariantOfUint16 = offsetKindVariantOfUint16 + Short.BYTES;
        buffer.putShort(offsetVariantOfUint16, (short) 50000);

        int offsetKindVariantOfUint32 = offsetVariantOfUint16 + Short.BYTES;
        buffer.putInt(offsetKindVariantOfUint32, (int) EnumWithUint32.NI.value());
        int offsetVariantOfUint32 = offsetKindVariantOfUint32 + Integer.BYTES;
        buffer.putInt(offsetVariantOfUint32, (int) 4000000000L);

        int offsetKindVariantOfString32 = offsetVariantOfUint32 + Integer.BYTES;
        buffer.putByte(offsetKindVariantOfString32, KIND_STRING8.value());
        int offsetVariantOfString32 = offsetKindVariantOfString32 + Byte.BYTES;
        buffer.putByte(offsetVariantOfString32, (byte) 7);
        buffer.putBytes(offsetVariantOfString32 + 1, "variant".getBytes());


        assertSame(listWithVariantOfIntRO, listWithVariantOfIntRO.wrap(buffer, offsetLength,
            offsetLength + length));
        assertEquals(length, listWithVariantOfIntRO.limit() - offsetLength);
        assertEquals(fieldCount, listWithVariantOfIntRO.fieldCount());
        assertEquals(1, listWithVariantOfIntRO.intField1());
        assertEquals(100000, listWithVariantOfIntRO.variantOfInt64());
        assertEquals(100, listWithVariantOfIntRO.variantOfInt8());
        assertEquals(30000, listWithVariantOfIntRO.intField2());
        assertEquals(2000, listWithVariantOfIntRO.variantOfInt16());
        assertEquals(-500, listWithVariantOfIntRO.variantOfInt32());
        assertEquals(200, listWithVariantOfIntRO.variantOfUint8());
        assertEquals(50000, listWithVariantOfIntRO.variantOfUint16());
        assertEquals(4000000000L, listWithVariantOfIntRO.variantOfUint32());
        assertEquals("variant", listWithVariantOfIntRO.variantOfString32().asString());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        byte length = 50;
        byte fieldCount = 10;
        long bitmask = 0x03FF;
        int offsetLength = 10;
        buffer.putByte(offsetLength, length);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, fieldCount);
        int offsetBitMask = offsetFieldCount + logicalLengthSize;
        buffer.putLong(offsetBitMask, bitmask);

        int offsetIntField1 = offsetBitMask + bitmaskSize;
        buffer.putByte(offsetIntField1, (byte) 1);

        int offsetKindVariantOfInt64 = offsetIntField1 + Byte.BYTES;
        buffer.putByte(offsetKindVariantOfInt64, (byte) 113);
        int offsetVariantOfInt64 = offsetKindVariantOfInt64 + Byte.BYTES;
        buffer.putInt(offsetVariantOfInt64, 100000);

        int offsetKindVariantOfInt8 = offsetVariantOfInt64 + Integer.BYTES;
        buffer.putByte(offsetKindVariantOfInt8, EnumWithInt8.ONE.value());
        int offsetVariantOfInt8 = offsetKindVariantOfInt8 + Byte.BYTES;
        buffer.putByte(offsetVariantOfInt8, (byte) 100);

        int offsetIntField2 = offsetVariantOfInt8 + Byte.BYTES;
        buffer.putShort(offsetIntField2, (short) 30000);

        int offsetKindVariantOfInt16 = offsetIntField2 + Short.BYTES;
        buffer.putShort(offsetKindVariantOfInt16, EnumWithInt16.THREE.value());
        int offsetVariantOfInt16 = offsetKindVariantOfInt16 + Short.BYTES;
        buffer.putShort(offsetVariantOfInt16, (short) 2000);

        int offsetKindVariantOfInt32 = offsetVariantOfInt16 + Short.BYTES;
        buffer.putByte(offsetKindVariantOfInt32, EnumWithInt8.TWO.value());
        int offsetVariantOfInt32 = offsetKindVariantOfInt32 + Byte.BYTES;
        buffer.putShort(offsetVariantOfInt32, (short) -500);

        int offsetKindVariantOfUint8 = offsetVariantOfInt32 + Short.BYTES;
        buffer.putByte(offsetKindVariantOfUint8, (byte) EnumWithUint8.ICHI.value());
        int offsetVariantOfUint8 = offsetKindVariantOfUint8 + Byte.BYTES;
        buffer.putByte(offsetVariantOfUint8, (byte) 200);

        int offsetKindVariantOfUint16 = offsetVariantOfUint8 + Byte.BYTES;
        buffer.putShort(offsetKindVariantOfUint16, (short) EnumWithUint16.ICHI.value());
        int offsetVariantOfUint16 = offsetKindVariantOfUint16 + Short.BYTES;
        buffer.putShort(offsetVariantOfUint16, (short) 50000);

        int offsetKindVariantOfUint32 = offsetVariantOfUint16 + Short.BYTES;
        buffer.putInt(offsetKindVariantOfUint32, (int) EnumWithUint32.NI.value());
        int offsetVariantOfUint32 = offsetKindVariantOfUint32 + Integer.BYTES;
        buffer.putInt(offsetVariantOfUint32, (int) 4000000000L);

        int offsetKindVariantOfString32 = offsetVariantOfUint32 + Integer.BYTES;
        buffer.putByte(offsetKindVariantOfString32, KIND_STRING8.value());
        int offsetVariantOfString32 = offsetKindVariantOfString32 + Byte.BYTES;
        buffer.putByte(offsetVariantOfString32, (byte) 7);
        buffer.putBytes(offsetVariantOfString32 + 1, "variant".getBytes());

        assertSame(listWithVariantOfIntRO, listWithVariantOfIntRO.tryWrap(buffer, offsetLength,
            offsetLength + length));
        assertEquals(length, listWithVariantOfIntRO.limit() - offsetLength);
        assertEquals(fieldCount, listWithVariantOfIntRO.fieldCount());
        assertEquals(1, listWithVariantOfIntRO.intField1());
        assertEquals(100000, listWithVariantOfIntRO.variantOfInt64());
        assertEquals(100, listWithVariantOfIntRO.variantOfInt8());
        assertEquals(30000, listWithVariantOfIntRO.intField2());
        assertEquals(2000, listWithVariantOfIntRO.variantOfInt16());
        assertEquals(-500, listWithVariantOfIntRO.variantOfInt32());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpace()
    {
        listWithVariantOfIntRW.wrap(buffer, 10, 18)
            .variantOfInt64(100000L);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetSameFieldTwice() throws Exception
    {
        listWithVariantOfIntRW.wrap(buffer, 0, buffer.capacity())
            .variantOfInt64(100000L)
            .variantOfInt64(100000L)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetFieldsInWrongOrder() throws Exception
    {
        listWithVariantOfIntRW.wrap(buffer, 0, buffer.capacity())
            .variantOfInt64(1000000L)
            .intField1((byte) 5)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenRequiredFieldIsNotSet() throws Exception
    {
        listWithVariantOfIntRW.wrap(buffer, 0, buffer.capacity())
            .variantOfInt64(1000000L)
            .intField1((byte) 5)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertErrorWhenAccessValueNotPresent() throws Exception
    {
        int limit = listWithVariantOfIntRW.wrap(buffer, 0, buffer.capacity())
            .variantOfInt64(100000L)
            .variantOfInt8(100)
            .variantOfInt16(2000)
            .variantOfInt32(-500)
            .variantOfUint8(200)
            .variantOfUint16(50000)
            .variantOfUint32(4000000000L)
            .variantOfString32(asStringFW("variant"))
            .build()
            .limit();
        listWithVariantOfIntRO.wrap(buffer,  0,  limit);
        assertEquals(5, listWithVariantOfIntRO.intField1());
    }

    @Test
    public void shouldSetSomeValues() throws Exception
    {
        int limit = listWithVariantOfIntRW.wrap(buffer, 0, buffer.capacity())
            .variantOfInt64(100000L)
            .variantOfInt8(100)
            .variantOfInt16(2000)
            .variantOfInt32(-500)
            .variantOfUint8(200)
            .build()
            .limit();
        listWithVariantOfIntRO.wrap(buffer,  0,  limit);
        assertEquals(100000L, listWithVariantOfIntRO.variantOfInt64());
        assertEquals(100, listWithVariantOfIntRO.variantOfInt8());
        assertEquals(2000, listWithVariantOfIntRO.variantOfInt16());
        assertEquals(-500, listWithVariantOfIntRO.variantOfInt32());
        assertEquals(200, listWithVariantOfIntRO.variantOfUint8());
        assertEquals(60000, listWithVariantOfIntRO.variantOfUint16());
        assertEquals(0, listWithVariantOfIntRO.variantOfUint32());
    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        int limit = listWithVariantOfIntRW.wrap(buffer, 0, buffer.capacity())
            .intField1((byte) 5)
            .variantOfInt64(100000L)
            .variantOfInt8(100)
            .intField2((short) 30000)
            .variantOfInt16(2000)
            .variantOfInt32(-500)
            .variantOfUint8(200)
            .variantOfUint16(50000)
            .variantOfUint32(4000000000L)
            .variantOfString32(asStringFW("variant"))
            .build()
            .limit();
        listWithVariantOfIntRO.wrap(buffer,  0,  limit);
        assertEquals(5, listWithVariantOfIntRO.intField1());
        assertEquals(100000L, listWithVariantOfIntRO.variantOfInt64());
        assertEquals(100, listWithVariantOfIntRO.variantOfInt8());
        assertEquals(30000, listWithVariantOfIntRO.intField2());
        assertEquals(2000, listWithVariantOfIntRO.variantOfInt16());
        assertEquals(-500, listWithVariantOfIntRO.variantOfInt32());
        assertEquals(200, listWithVariantOfIntRO.variantOfUint8());
        assertEquals(50000, listWithVariantOfIntRO.variantOfUint16());
        assertEquals(4000000000L, listWithVariantOfIntRO.variantOfUint32());
        assertEquals("variant", listWithVariantOfIntRO.variantOfString32().asString());
    }

    private static StringFW asStringFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
