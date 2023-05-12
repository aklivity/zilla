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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.MapFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String16FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String32FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.ListWithMapFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.TypedefStringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantEnumKindOfStringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantOfMapFW;

public class ListWithMapFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final ListWithMapFW.Builder flyweightRW = new ListWithMapFW.Builder();
    private final ListWithMapFW flyweightRO = new ListWithMapFW();

    private static final EnumWithInt8 KIND_MAP8 = EnumWithInt8.SIX;
    private static final EnumWithInt8 KIND_STRING8 = EnumWithInt8.NINE;
    private static final EnumWithInt8 KIND_LIST8 = EnumWithInt8.TWO;
    private final int kindSize = Byte.BYTES;
    private final int lengthSize = Byte.BYTES;
    private final int fieldCountSize = Byte.BYTES;

    private int setAlFields(
        MutableDirectBuffer buffer,
        int offset)
    {
        byte listLength = 60;
        byte listFieldCount = 2;
        byte mapLength = 49;
        byte mapFieldCount = 4;
        String field1 = "field1";
        String entry1Key = "entry1Key";
        String entry1Value = "entry1Value";
        String entry2Key = "entry2Key";
        String entry2Value = "entry2Value";

        buffer.putByte(offset, KIND_LIST8.value());
        int offsetListLength = offset + kindSize;
        buffer.putByte(offsetListLength, listLength);
        int offsetListFieldCount = offsetListLength + kindSize;
        buffer.putByte(offsetListFieldCount, listFieldCount);

        int offsetField1Kind = offsetListFieldCount + fieldCountSize;
        buffer.putByte(offsetField1Kind, KIND_STRING8.value());
        int offsetField1Length = offsetField1Kind + kindSize;
        buffer.putByte(offsetField1Length, (byte) field1.length());
        int offsetField1 = offsetField1Length + Byte.BYTES;
        buffer.putBytes(offsetField1, field1.getBytes());

        int offsetMapKind = offsetField1 + (byte) field1.length();
        buffer.putByte(offsetMapKind, KIND_MAP8.value());
        int offsetLength = offsetMapKind + kindSize;
        buffer.putByte(offsetLength, mapLength);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, mapFieldCount);

        int offsetMapEntry1KeyKind = offsetFieldCount + fieldCountSize;
        buffer.putByte(offsetMapEntry1KeyKind, KIND_STRING8.value());
        int offsetEntry1KeyLength = offsetMapEntry1KeyKind + Byte.BYTES;
        buffer.putByte(offsetEntry1KeyLength, (byte) entry1Key.length());
        int offsetEntry1Key = offsetEntry1KeyLength + Byte.BYTES;
        buffer.putBytes(offsetEntry1Key, entry1Key.getBytes());

        int offsetMapEntry1ValueKind = offsetEntry1Key + entry1Key.length();
        buffer.putByte(offsetMapEntry1ValueKind, KIND_STRING8.value());
        int offsetEntry1ValueLength = offsetMapEntry1ValueKind + Byte.BYTES;
        buffer.putByte(offsetEntry1ValueLength, (byte) entry1Value.length());
        int offsetEntry1Value = offsetEntry1ValueLength + Byte.BYTES;
        buffer.putBytes(offsetEntry1Value, entry1Value.getBytes());

        int offsetMapEntry2KeyKind = offsetEntry1Value + entry1Value.length();
        buffer.putByte(offsetMapEntry2KeyKind, KIND_STRING8.value());
        int offsetEntry2KeyLength = offsetMapEntry2KeyKind + Byte.BYTES;
        buffer.putByte(offsetEntry2KeyLength, (byte) entry2Key.length());
        int offsetEntry2Key = offsetEntry2KeyLength + Byte.BYTES;
        buffer.putBytes(offsetEntry2Key, entry2Key.getBytes());

        int offsetMapEntry2ValueKind = offsetEntry2Key + entry2Key.length();
        buffer.putByte(offsetMapEntry2ValueKind, KIND_STRING8.value());
        int offsetEntry2ValueLength = offsetMapEntry2ValueKind + Byte.BYTES;
        buffer.putByte(offsetEntry2ValueLength, (byte) entry2Value.length());
        int offsetEntry2Value = offsetEntry2ValueLength + Byte.BYTES;
        buffer.putBytes(offsetEntry2Value, entry2Value.getBytes());

        return listLength + kindSize + lengthSize;
    }

    static void assertAllTestValuesRead(
        ListWithMapFW flyweight,
        int offset)
    {
        assertEquals(60, flyweight.length());
        assertEquals(2, flyweight.fieldCount());
        assertEquals(offset + 62, flyweight.limit());
        assertEquals("field1", flyweight.field1().asString());
        assertEquals(6, flyweight.field1().length());
        List<String> mapItems = new ArrayList<>();
        flyweight.mapOfString().forEach((k, v) ->
        {
            mapItems.add(k.get().asString());
            mapItems.add(v.get().asString());
        });
        assertEquals(49, flyweight.mapOfString().length());
        assertEquals(4, flyweight.mapOfString().fieldCount());
        assertEquals(4, mapItems.size());
        assertEquals("entry1Key", mapItems.get(0));
        assertEquals("entry1Value", mapItems.get(1));
        assertEquals("entry2Key", mapItems.get(2));
        assertEquals("entry2Value", mapItems.get(3));
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setAlFields(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            try
            {
                flyweightRO.wrap(buffer, 10, maxLimit);
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
        int length = setAlFields(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setAlFields(buffer, 10);

        final ListWithMapFW listWithMap = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, listWithMap);
        assertAllTestValuesRead(listWithMap, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setAlFields(buffer, 10);

        final ListWithMapFW listWithMap = flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, listWithMap);
        assertAllTestValuesRead(listWithMap, 10);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenFieldIsSetOutOfOrder() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .mapOfString(asMapFW(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(asStringFW("entry1Value"), asStringFW("entry2Value"))))
            .field1(asStringFW("field1"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSameFieldIsSetMoreThanOnce() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field1(asStringFW("field1"))
            .mapOfString(asMapFW(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(asStringFW("entry1Value"), asStringFW("entry2Value"))))
            .mapOfString(asMapFW(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(asStringFW("entry1Value"), asStringFW("entry2Value"))))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenRequiredFieldIsNotSet() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenRequiredFieldIsNotSet() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .mapOfString(asMapFW(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(asStringFW("entry1Value"), asStringFW("entry2Value"))));
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertErrorWhenValueNotPresent() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field1(asStringFW("field1"))
            .build()
            .limit();

        final ListWithMapFW listWithMap = flyweightRO.wrap(buffer, 0, limit);

        listWithMap.mapOfString();
    }

    @Test
    public void shouldSetAllFields() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .field1(asStringFW("field1"))
            .mapOfString(asMapFW(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(asStringFW("entry1Value"), asStringFW("entry2Value"))))
            .build()
            .limit();

        final ListWithMapFW listWithMap = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(listWithMap, 0);
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
            buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
            return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
        case 1:
            buffer = new UnsafeBuffer(allocateDirect(Short.SIZE + value.length()));
            return new String16FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
        case 2:
        case 3:
            buffer = new UnsafeBuffer(allocateDirect(Integer.SIZE + value.length()));
            return new String32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
        default:
            throw new IllegalArgumentException("Illegal value: " + value);
        }
    }

    private static MapFW<VariantEnumKindOfStringFW, TypedefStringFW> asMapFW(
        List<StringFW> keys,
        List<StringFW> values)
    {
        VariantOfMapFW.Builder<VariantEnumKindOfStringFW, TypedefStringFW,
            VariantEnumKindOfStringFW.Builder, TypedefStringFW.Builder> variantOfMapRW =
            new VariantOfMapFW.Builder<>(new VariantEnumKindOfStringFW(), new TypedefStringFW(),
                new VariantEnumKindOfStringFW.Builder(), new TypedefStringFW.Builder());
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100));
        variantOfMapRW.wrap(buffer, 0, buffer.capacity());
        for (int i = 0; i < keys.size(); i++)
        {
            StringFW key = keys.get(i);
            StringFW value = values.get(i);
            variantOfMapRW.entry(k -> k.set(key), v -> v.set(value));
        }
        VariantOfMapFW<VariantEnumKindOfStringFW, TypedefStringFW> variantOfMapRO = variantOfMapRW.build();
        return variantOfMapRO.get();
    }
}
