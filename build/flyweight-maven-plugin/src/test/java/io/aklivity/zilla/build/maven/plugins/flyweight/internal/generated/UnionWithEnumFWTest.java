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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithVariantOfUint64;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.ListWithEnumAndVariantWithDefaultFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.UnionWithEnumFW;

public class UnionWithEnumFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final UnionWithEnumFW.Builder flyweightRW = new UnionWithEnumFW.Builder();
    private final UnionWithEnumFW flyweightRO = new UnionWithEnumFW();

    private final int lengthSize = Byte.BYTES;
    private final int fieldCountSize = Byte.BYTES;
    private static final EnumWithInt8 LIST_KIND = EnumWithInt8.TWO;
    private static final EnumWithInt8 FIELD_TYPE_KIND = EnumWithInt8.ONE;
    private static final EnumWithInt8 FIELD3_TYPE_KIND = EnumWithInt8.FIVE;
    private static final int FIELD_VALUE_KIND_UINT8 = 83;
    private static final EnumWithUint8 KIND_UINT8_ENUM = EnumWithUint8.ICHI;
    private static final EnumWithUint8 KIND_FIELD4 = EnumWithUint8.SAN;

    private int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        int unionCaseTypeOffset = offset;
        buffer.putByte(unionCaseTypeOffset, EnumWithInt8.ONE.value());
        int unionCaseValueKindOffset = unionCaseTypeOffset + Byte.BYTES;
        buffer.putByte(unionCaseValueKindOffset, (byte) FIELD_VALUE_KIND_UINT8);
        int unionCaseValueOffset = unionCaseValueKindOffset + Byte.BYTES;
        buffer.putByte(unionCaseValueOffset, (byte) EnumWithVariantOfUint64.TYPE2.value());

        byte listLength = 12;
        byte listFieldCount = 5;
        int listKindOffset = unionCaseValueOffset + Byte.BYTES;
        buffer.putByte(listKindOffset, LIST_KIND.value());
        int offsetLength = listKindOffset + Byte.BYTES;
        buffer.putByte(offsetLength, listLength);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, listFieldCount);

        int offsetField1Kind = offsetFieldCount + fieldCountSize;
        buffer.putByte(offsetField1Kind, FIELD_TYPE_KIND.value());
        int offsetField1ValueKind = offsetField1Kind + Byte.BYTES;
        buffer.putByte(offsetField1ValueKind, (byte) FIELD_VALUE_KIND_UINT8);
        int offsetField1Value = offsetField1ValueKind + Byte.BYTES;
        buffer.putByte(offsetField1Value, (byte) EnumWithVariantOfUint64.TYPE3.value());

        int offsetField2Kind = offsetField1Value + Byte.BYTES;
        buffer.putByte(offsetField2Kind, FIELD_TYPE_KIND.value());
        int offsetField2ValueKind = offsetField2Kind + Byte.BYTES;
        buffer.putByte(offsetField2ValueKind, (byte) FIELD_VALUE_KIND_UINT8);
        int offsetField2Value = offsetField2ValueKind + Byte.BYTES;
        buffer.putByte(offsetField2Value, (byte) EnumWithVariantOfUint64.TYPE4.value());

        int offsetField3Kind = offsetField2Value + Byte.BYTES;
        buffer.putByte(offsetField3Kind, FIELD3_TYPE_KIND.value());
        int offsetField3Value = offsetField3Kind + Byte.BYTES;
        buffer.putByte(offsetField3Value, (byte) 100);

        int offsetField4Value = offsetField3Value + Byte.BYTES;
        buffer.putByte(offsetField4Value, (byte) KIND_FIELD4.value());

        int offsetField5Kind = offsetField4Value + Byte.BYTES;
        buffer.putByte(offsetField5Kind, (byte) KIND_UINT8_ENUM.value());
        int offsetField5Value = offsetField5Kind + Byte.BYTES;
        buffer.putByte(offsetField5Value, (byte) 30);
        return listLength + (5 * Byte.BYTES);
    }

    static void assertAllTestValuesRead(
        UnionWithEnumFW unionWithEnum)
    {
        assertEquals(EnumWithVariantOfUint64.TYPE2, unionWithEnum.kind());
        ListWithEnumAndVariantWithDefaultFW list = unionWithEnum.listValue();
        assertNotNull(list);
        assertEquals(12, list.length());
        assertEquals(5, list.fieldCount());
        assertEquals(EnumWithVariantOfUint64.TYPE3, list.field1());
        assertEquals(EnumWithVariantOfUint64.TYPE4, list.field2());
        assertEquals(100, list.field3());
        assertEquals(1, list.field4());
        assertEquals(30, list.field5());
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenIncomplete()
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
    public void shouldTryWrapWhenLengthSufficientCase1()
    {
        int size = setAllTestValues(buffer, 10);
        assertSame(flyweightRO, flyweightRO.tryWrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldWrapWhenLengthSufficientCase1()
    {
        int size = setAllTestValues(buffer, 10);
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldTryWrapAndReadAllValuesCase8() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);

        final UnionWithEnumFW unionWithEnum = flyweightRO.tryWrap(buffer, offset, buffer.capacity());

        assertNotNull(unionWithEnum);
        assertAllTestValuesRead(unionWithEnum);
    }

    @Test
    public void shouldWrapAndReadAllValuesCase1() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);

        final UnionWithEnumFW unionWithEnum = flyweightRO.wrap(buffer, offset, buffer.capacity());

        assertAllTestValuesRead(unionWithEnum);
    }

    @Test
    public void shouldSetList()
    {
        MutableDirectBuffer listBuffer = new UnsafeBuffer(allocateDirect(20));
        ListWithEnumAndVariantWithDefaultFW listWithEnumAndVariantWithDefault =
            new ListWithEnumAndVariantWithDefaultFW.Builder()
                .wrap(listBuffer, 0, listBuffer.capacity())
                .field1(EnumWithVariantOfUint64.TYPE3)
                .field2(EnumWithVariantOfUint64.TYPE4)
                .field3(100)
                .field5(30)
                .build();

        final UnionWithEnumFW unionWithEnum = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .listValue(listWithEnumAndVariantWithDefault)
            .build();

        assertAllTestValuesRead(unionWithEnum);
    }
}
