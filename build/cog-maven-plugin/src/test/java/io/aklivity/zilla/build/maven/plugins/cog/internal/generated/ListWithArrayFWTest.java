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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.ArrayFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String16FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.ListWithArrayFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantEnumKindOfStringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantOfArrayFW;

public class ListWithArrayFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final ListWithArrayFW.Builder listWithArrayRW = new ListWithArrayFW.Builder();
    private final ListWithArrayFW listWithArrayRO = new ListWithArrayFW();
    private final int lengthSize = Byte.BYTES;
    private final int fieldCountSize = Byte.BYTES;
    private final byte kindList8 = EnumWithInt8.TWO.value();
    private final int kindSize = Byte.BYTES;
    private final byte kindArray8 = EnumWithInt8.EIGHT.value();
    private final byte kindString8 = EnumWithInt8.NINE.value();

    static void assertAllTestValuesRead(
        ArrayFW<VariantEnumKindOfStringFW> flyweight)
    {
        List<String> arrayItems = new ArrayList<>();
        flyweight.forEach(v -> arrayItems.add(v.get().asString()));
        assertEquals(2, arrayItems.size());
        assertEquals("symbolA", arrayItems.get(0));
        assertEquals("symbolB", arrayItems.get(1));
        assertEquals(18, flyweight.length());
        assertEquals(2, flyweight.fieldCount());
    }

    private void setAllFields(
        MutableDirectBuffer buffer)
    {
        byte listLength = 29;
        byte listFieldCount = 2;
        byte arrayLength = 18;
        byte arrayFieldCount = 2;
        int offsetListKind = 10;
        buffer.putByte(offsetListKind, kindList8);
        int offsetListLength = offsetListKind + kindSize;
        buffer.putByte(offsetListLength, listLength);
        int offsetListFieldCount = offsetListLength + lengthSize;
        buffer.putByte(offsetListFieldCount, listFieldCount);

        int offsetField1Kind = offsetListFieldCount + fieldCountSize;
        buffer.putByte(offsetField1Kind, EnumWithInt8.NINE.value());
        int offsetField1Length = offsetField1Kind + Byte.BYTES;
        buffer.putByte(offsetField1Length, (byte) "field1".length());
        int offsetField1 = offsetField1Length + Byte.BYTES;
        buffer.putBytes(offsetField1, "field1".getBytes());

        int offsetArrayOfStringKind = offsetField1 + "field1".length();
        buffer.putByte(offsetArrayOfStringKind, kindArray8);
        int offsetArrayLength = offsetArrayOfStringKind + kindSize;
        buffer.putByte(offsetArrayLength, arrayLength);
        int offsetArrayFieldCount = offsetArrayLength + Byte.BYTES;
        buffer.putByte(offsetArrayFieldCount, arrayFieldCount);

        int offsetArrayItemKind = offsetArrayFieldCount + Byte.BYTES;
        buffer.putByte(offsetArrayItemKind, kindString8);

        int offsetArrayItem1Length = offsetArrayItemKind + Byte.BYTES;
        buffer.putByte(offsetArrayItem1Length, (byte) "symbolA".length());
        int offsetArrayItem1Value = offsetArrayItem1Length + Byte.BYTES;
        buffer.putBytes(offsetArrayItem1Value, "symbolA".getBytes());

        int offsetArrayItem2Length = offsetArrayItem1Value + "symbolA".length();
        buffer.putByte(offsetArrayItem2Length, (byte) "symbolB".length());
        int offsetArrayItem2Value = offsetArrayItem2Length + Byte.BYTES;
        buffer.putBytes(offsetArrayItem2Value, "symbolB".getBytes());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = 29;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            listWithArrayRO.wrap(buffer,  10, maxLimit);
        }
    }

    @Test
    public void shouldNotTryWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = 29;
        int offsetLength = 10;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            assertNull(listWithArrayRO.tryWrap(buffer,  offsetLength, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 29;
        int kindSize = Byte.BYTES;
        int lengthSize = Byte.BYTES;
        int fieldCount = 2;
        int offsetLength = 10;
        int maxLimit = offsetLength + kindSize + lengthSize + length;
        setAllFields(buffer);

        final ListWithArrayFW listWithArray = listWithArrayRO.wrap(buffer, offsetLength, maxLimit);

        assertSame(listWithArrayRO, listWithArray);
        assertEquals(length, listWithArray.length());
        assertEquals(fieldCount, listWithArray.fieldCount());
        assertEquals(offsetLength + 31, listWithArray.limit());
        assertEquals(2, listWithArray.fieldCount());
        assertEquals(29, listWithArray.length());
        assertEquals("field1", listWithArray.field1().asString());
        assertAllTestValuesRead(listWithArray.arrayOfString());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 29;
        int kindSize = Byte.BYTES;
        int lengthSize = Byte.BYTES;
        int fieldCount = 2;
        int offsetLength = 10;
        int maxLimit = offsetLength + kindSize + lengthSize + length;
        setAllFields(buffer);

        final ListWithArrayFW listWithArray = listWithArrayRO.tryWrap(buffer, offsetLength, maxLimit);

        assertSame(listWithArrayRO, listWithArray);
        assertEquals(length, listWithArray.length());
        assertEquals(fieldCount, listWithArray.fieldCount());
        assertEquals(offsetLength + 31, listWithArray.limit());
        assertEquals(2, listWithArray.fieldCount());
        assertEquals(29, listWithArray.length());
        assertEquals("field1", listWithArray.field1().asString());
        assertAllTestValuesRead(listWithArray.arrayOfString());
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenFieldIsSetOutOfOrder() throws Exception
    {
        listWithArrayRW.wrap(buffer, 0, buffer.capacity())
            .arrayOfString(asArrayFW(Arrays.asList(asStringFW("symbolA"), asStringFW("symbolB"))))
            .field1(asStringFW("field1"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSameFieldIsSetMoreThanOnce() throws Exception
    {
        listWithArrayRW.wrap(buffer, 0, buffer.capacity())
            .field1(asStringFW("field1"))
            .field1(asStringFW("field1"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenRequiredFieldIsNotSet() throws Exception
    {
        listWithArrayRW.wrap(buffer, 0, buffer.capacity())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenRequiredFieldIsNotSet() throws Exception
    {
        listWithArrayRW.wrap(buffer, 0, buffer.capacity())
            .arrayOfString(asArrayFW(Arrays.asList(asStringFW("symbolA"), asStringFW("symbolB"))));
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertErrorWhenValueNotPresent() throws Exception
    {
        int limit = listWithArrayRW.wrap(buffer, 0, buffer.capacity())
            .field1(asStringFW("field1"))
            .build()
            .limit();

        final ListWithArrayFW listWithArray = listWithArrayRO.wrap(buffer, 0, limit);

        listWithArray.arrayOfString();
    }

    @Test
    public void shouldSetSomeFields() throws Exception
    {
        int limit = listWithArrayRW.wrap(buffer, 0, buffer.capacity())
            .field1(asStringFW("field1"))
            .build()
            .limit();

        final ListWithArrayFW listWithArray = listWithArrayRO.wrap(buffer, 0, limit);

        assertEquals(11, listWithArray.limit());
        assertEquals(1, listWithArray.fieldCount());
        assertEquals(9, listWithArray.length());
        assertEquals("field1", listWithArray.field1().asString());
    }

    @Test
    public void shouldSetAllFields() throws Exception
    {
        int limit = listWithArrayRW.wrap(buffer, 0, buffer.capacity())
            .field1(asStringFW("field1"))
            .arrayOfString(asArrayFW(Arrays.asList(asStringFW("symbolA"), asStringFW("symbolB"))))
            .build()
            .limit();

        final ListWithArrayFW listWithArray = listWithArrayRO.wrap(buffer, 0, limit);

        assertEquals(31, listWithArray.limit());
        assertEquals(2, listWithArray.fieldCount());
        assertEquals(29, listWithArray.length());
        assertEquals("field1", listWithArray.field1().asString());
        assertAllTestValuesRead(listWithArray.arrayOfString());
    }

    private static StringFW asStringFW(
        String value)
    {
        int length = value.length();
        int highestByteIndex = Integer.numberOfTrailingZeros(Integer.highestOneBit(length)) >> 3;
        MutableDirectBuffer buffer;
        switch (highestByteIndex)
        {
        case 0:
            buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + value.length()));
            return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
        case 1:
            buffer = new UnsafeBuffer(allocateDirect(Short.BYTES + value.length()));
            return new String16FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
        case 2:
        case 3:
            buffer = new UnsafeBuffer(allocateDirect(Integer.BYTES + value.length()));
            return new String32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
        default:
            throw new IllegalArgumentException("Illegal value: " + value);
        }
    }

    private static ArrayFW<VariantEnumKindOfStringFW> asArrayFW(
        List<StringFW> values)
    {
        VariantOfArrayFW.Builder<VariantEnumKindOfStringFW.Builder, VariantEnumKindOfStringFW> variantOfArrayRW =
            new VariantOfArrayFW.Builder<>(new VariantEnumKindOfStringFW.Builder(), new VariantEnumKindOfStringFW());
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100));
        variantOfArrayRW.wrap(buffer, 0, buffer.capacity());
        for (StringFW value : values)
        {
            variantOfArrayRW.item(b -> b.setAsString32(value));
        }
        VariantOfArrayFW<VariantEnumKindOfStringFW> variantOfVariantArrayRO = variantOfArrayRW.build();
        return variantOfVariantArrayRO.get();
    }
}
