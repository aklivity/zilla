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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithVariantOfUint64;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.ListWithEnumAndVariantWithDefaultFW;

public class ListWithEnumAndVariantWithDefaultFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final ListWithEnumAndVariantWithDefaultFW.Builder listWithEnumAndVariantWithDefaultRW =
        new ListWithEnumAndVariantWithDefaultFW.Builder();
    private final ListWithEnumAndVariantWithDefaultFW listWithEnumAndVariantWithDefaultRO =
        new ListWithEnumAndVariantWithDefaultFW();

    private final int lengthSize = Byte.BYTES;
    private final int fieldCountSize = Byte.BYTES;
    private static final EnumWithInt8 KIND_LIST8 = EnumWithInt8.TWO;
    private static final EnumWithInt8 KIND_ONE = EnumWithInt8.ONE;
    public static final EnumWithInt8 KIND_INT8 = EnumWithInt8.FIVE;
    private static final int KIND_UINT8 = 83;
    public static final EnumWithUint8 KIND_UINT8_ENUM = EnumWithUint8.ICHI;
    public static final EnumWithUint8 KIND_FIELD4 = EnumWithUint8.SAN;

    private void setAllFields(
        MutableDirectBuffer buffer)
    {
        byte length = 12;
        byte fieldCount = 5;
        int listKindOffset = 10;
        buffer.putByte(listKindOffset, KIND_LIST8.value());
        int offsetLength = listKindOffset + Byte.BYTES;
        buffer.putByte(offsetLength, length);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, fieldCount);

        int offsetField1Kind = offsetFieldCount + fieldCountSize;
        buffer.putByte(offsetField1Kind, KIND_ONE.value());
        int offsetField1ValueKind = offsetField1Kind + Byte.BYTES;
        buffer.putByte(offsetField1ValueKind, (byte) KIND_UINT8);
        int offsetField1Value = offsetField1ValueKind + Byte.BYTES;
        buffer.putByte(offsetField1Value, (byte) EnumWithVariantOfUint64.TYPE3.value());

        int offsetField2Kind = offsetField1Value + Byte.BYTES;
        buffer.putByte(offsetField2Kind, KIND_ONE.value());
        int offsetField2ValueKind = offsetField2Kind + Byte.BYTES;
        buffer.putByte(offsetField2ValueKind, (byte) KIND_UINT8);
        int offsetField2Value = offsetField2ValueKind + Byte.BYTES;
        buffer.putByte(offsetField2Value, (byte) EnumWithVariantOfUint64.TYPE4.value());

        int offsetField3Kind = offsetField2Value + Byte.BYTES;
        buffer.putByte(offsetField3Kind, KIND_INT8.value());
        int offsetField3Value = offsetField3Kind + Byte.BYTES;
        buffer.putByte(offsetField3Value, (byte) 100);

        int offsetField4Value = offsetField3Value + Byte.BYTES;
        buffer.putByte(offsetField4Value, (byte) KIND_FIELD4.value());

        int offsetField5Kind = offsetField4Value + Byte.BYTES;
        buffer.putByte(offsetField5Kind, (byte) KIND_UINT8_ENUM.value());
        int offsetField5Value = offsetField5Kind + Byte.BYTES;
        buffer.putByte(offsetField5Value, (byte) 30);
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = 12;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            try
            {
                listWithEnumAndVariantWithDefaultRO.wrap(buffer, 10, maxLimit);
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
        int length = 12;
        int offsetLength = 10;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            assertNull(listWithEnumAndVariantWithDefaultRO.tryWrap(buffer, offsetLength, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 12;
        int kindSize = Byte.BYTES;
        int lengthSize = Byte.BYTES;
        int fieldCount = 5;
        int offsetLength = 10;
        int maxLimit = offsetLength + kindSize + lengthSize + length;
        setAllFields(buffer);

        final ListWithEnumAndVariantWithDefaultFW listWithEnumAndVariantWithDefault =
            listWithEnumAndVariantWithDefaultRO.wrap(buffer, offsetLength, maxLimit);

        assertSame(listWithEnumAndVariantWithDefaultRO, listWithEnumAndVariantWithDefault);
        assertEquals(length, listWithEnumAndVariantWithDefault.length());
        assertEquals(fieldCount, listWithEnumAndVariantWithDefault.fieldCount());
        assertEquals(EnumWithVariantOfUint64.TYPE3, listWithEnumAndVariantWithDefault.field1());
        assertEquals(EnumWithVariantOfUint64.TYPE4, listWithEnumAndVariantWithDefault.field2());
        assertEquals(100, listWithEnumAndVariantWithDefault.field3());
        assertEquals(1, listWithEnumAndVariantWithDefault.field4());
        assertEquals(30, listWithEnumAndVariantWithDefault.field5());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 12;
        int kindSize = Byte.BYTES;
        int lengthSize = Byte.BYTES;
        int fieldCount = 5;
        int offsetLength = 10;
        int maxLimit = offsetLength + kindSize + lengthSize + length;
        setAllFields(buffer);

        final ListWithEnumAndVariantWithDefaultFW listWithEnumAndVariantWithDefault =
            listWithEnumAndVariantWithDefaultRO.tryWrap(buffer, offsetLength, maxLimit);

        assertSame(listWithEnumAndVariantWithDefaultRO, listWithEnumAndVariantWithDefault);
        assertEquals(length, listWithEnumAndVariantWithDefault.length());
        assertEquals(fieldCount, listWithEnumAndVariantWithDefault.fieldCount());
        assertEquals(EnumWithVariantOfUint64.TYPE3, listWithEnumAndVariantWithDefault.field1());
        assertEquals(EnumWithVariantOfUint64.TYPE4, listWithEnumAndVariantWithDefault.field2());
        assertEquals(100, listWithEnumAndVariantWithDefault.field3());
        assertEquals(1, listWithEnumAndVariantWithDefault.field4());
        assertEquals(30, listWithEnumAndVariantWithDefault.field5());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpace() throws Exception
    {
        listWithEnumAndVariantWithDefaultRW.wrap(buffer, 10, 11)
            .field1(EnumWithVariantOfUint64.TYPE3);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenFieldIsSetOutOfOrder() throws Exception
    {
        listWithEnumAndVariantWithDefaultRW.wrap(buffer, 0, buffer.capacity())
            .field1(EnumWithVariantOfUint64.TYPE3)
            .field2(EnumWithVariantOfUint64.TYPE4)
            .field5(30)
            .field3(1000)
            .field4(20)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSameFieldIsSetMoreThanOnce() throws Exception
    {
        listWithEnumAndVariantWithDefaultRW.wrap(buffer, 0, buffer.capacity())
            .field1(EnumWithVariantOfUint64.TYPE3)
            .field1(EnumWithVariantOfUint64.TYPE3)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenRequiredFieldIsNotSet() throws Exception
    {
        listWithEnumAndVariantWithDefaultRW.wrap(buffer, 0, buffer.capacity())
            .field5(30)
            .build()
            .limit();
    }

    @Test
    public void shouldSetField25() throws Exception
    {
        int limit = listWithEnumAndVariantWithDefaultRW.wrap(buffer, 0, buffer.capacity())
            .field2(EnumWithVariantOfUint64.TYPE4)
            .field5(30)
            .build()
            .limit();

        final ListWithEnumAndVariantWithDefaultFW listWithEnumAndVariantWithDefault =
            listWithEnumAndVariantWithDefaultRO.wrap(buffer, 0, limit);

        assertFalse(listWithEnumAndVariantWithDefault.hasField1());
        assertFalse(listWithEnumAndVariantWithDefault.hasField3());
        assertFalse(listWithEnumAndVariantWithDefault.hasField4());
        assertTrue(listWithEnumAndVariantWithDefault.hasField5());
        assertEquals(EnumWithVariantOfUint64.TYPE3, listWithEnumAndVariantWithDefault.field1());
        assertEquals(EnumWithVariantOfUint64.TYPE4, listWithEnumAndVariantWithDefault.field2());
        assertEquals(100, listWithEnumAndVariantWithDefault.field3());
        assertEquals(1, listWithEnumAndVariantWithDefault.field4());
        assertEquals(30, listWithEnumAndVariantWithDefault.field5());
    }

    @Test
    public void shouldSetField124() throws Exception
    {
        int limit = listWithEnumAndVariantWithDefaultRW.wrap(buffer, 0, buffer.capacity())
            .field1(EnumWithVariantOfUint64.TYPE3)
            .field2(EnumWithVariantOfUint64.TYPE4)
            .field4(20)
            .build()
            .limit();

        final ListWithEnumAndVariantWithDefaultFW listWithEnumAndVariantWithDefault =
            listWithEnumAndVariantWithDefaultRO.wrap(buffer, 0, limit);

        assertTrue(listWithEnumAndVariantWithDefault.hasField1());
        assertFalse(listWithEnumAndVariantWithDefault.hasField3());
        assertTrue(listWithEnumAndVariantWithDefault.hasField4());
        assertFalse(listWithEnumAndVariantWithDefault.hasField5());
        assertEquals(EnumWithVariantOfUint64.TYPE3, listWithEnumAndVariantWithDefault.field1());
        assertEquals(EnumWithVariantOfUint64.TYPE4, listWithEnumAndVariantWithDefault.field2());
        assertEquals(100, listWithEnumAndVariantWithDefault.field3());
        assertEquals(20, listWithEnumAndVariantWithDefault.field4());
    }

    @Test
    public void shouldSetFirstFourFields() throws Exception
    {
        int limit = listWithEnumAndVariantWithDefaultRW.wrap(buffer, 0, buffer.capacity())
            .field1(EnumWithVariantOfUint64.TYPE3)
            .field2(EnumWithVariantOfUint64.TYPE4)
            .field3(1000)
            .field4(20)
            .build()
            .limit();

        final ListWithEnumAndVariantWithDefaultFW listWithEnumAndVariantWithDefault =
            listWithEnumAndVariantWithDefaultRO.wrap(buffer, 0, limit);

        assertTrue(listWithEnumAndVariantWithDefault.hasField1());
        assertTrue(listWithEnumAndVariantWithDefault.hasField3());
        assertTrue(listWithEnumAndVariantWithDefault.hasField4());
        assertFalse(listWithEnumAndVariantWithDefault.hasField5());
        assertEquals(EnumWithVariantOfUint64.TYPE3, listWithEnumAndVariantWithDefault.field1());
        assertEquals(EnumWithVariantOfUint64.TYPE4, listWithEnumAndVariantWithDefault.field2());
        assertEquals(1000, listWithEnumAndVariantWithDefault.field3());
        assertEquals(20, listWithEnumAndVariantWithDefault.field4());
    }

    @Test
    public void shouldSetAllFields() throws Exception
    {
        int limit = listWithEnumAndVariantWithDefaultRW.wrap(buffer, 0, buffer.capacity())
            .field1(EnumWithVariantOfUint64.TYPE3)
            .field2(EnumWithVariantOfUint64.TYPE4)
            .field3(1000)
            .field4(20)
            .field5(30)
            .build()
            .limit();

        final ListWithEnumAndVariantWithDefaultFW listWithEnumAndVariantWithDefault =
            listWithEnumAndVariantWithDefaultRO.wrap(buffer, 0, limit);

        assertEquals(EnumWithVariantOfUint64.TYPE3, listWithEnumAndVariantWithDefault.field1());
        assertEquals(EnumWithVariantOfUint64.TYPE4, listWithEnumAndVariantWithDefault.field2());
        assertEquals(1000, listWithEnumAndVariantWithDefault.field3());
        assertEquals(20, listWithEnumAndVariantWithDefault.field4());
        assertEquals(30, listWithEnumAndVariantWithDefault.field5());
    }
}
