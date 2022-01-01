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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String16FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.ConstrainedMapFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.ListWithConstrainedMapFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantEnumKindOfStringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantOfInt32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantWithoutOfFW;

public class ListWithConstrainedMapFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final ListWithConstrainedMapFW.Builder flyweightRW = new ListWithConstrainedMapFW.Builder();
    private final ListWithConstrainedMapFW flyweightRO = new ListWithConstrainedMapFW();

    private static final EnumWithInt8 KIND_MAP8 = EnumWithInt8.SIX;
    private static final EnumWithInt8 KIND_STRING8 = EnumWithInt8.NINE;
    private static final EnumWithInt8 KIND_LIST8 = EnumWithInt8.TWO;
    private final int kindSize = Byte.BYTES;
    private final int lengthSize = Byte.BYTES;
    private final int fieldCountSize = Byte.BYTES;

    private int setStringEntries(
        MutableDirectBuffer buffer,
        int offset)
    {
        byte listLength = 52;
        byte listFieldCount = 1;
        byte mapLength = 49;
        byte mapFieldCount = 4;
        String entry1Key = "entry1Key";
        String entry1Value = "entry1Value";
        String entry2Key = "entry2Key";
        String entry2Value = "entry2Value";

        buffer.putByte(offset, KIND_LIST8.value());
        int offsetListLength = offset + kindSize;
        buffer.putByte(offsetListLength, listLength);
        int offsetListFieldCount = offsetListLength + kindSize;
        buffer.putByte(offsetListFieldCount, listFieldCount);

        int offsetMapKind = offsetListFieldCount + fieldCountSize;
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

    static void assertAllTestValuesReadWithStringValues(
        ListWithConstrainedMapFW flyweight,
        int offset)
    {
        assertEquals(52, flyweight.length());
        assertEquals(1, flyweight.fieldCount());
        assertEquals(offset + 54, flyweight.limit());
        List<String> mapItems = new ArrayList<>();
        flyweight.constrainedMap().forEach((k, v) ->
        {
            mapItems.add(k.get().asString());
            mapItems.add(v.getAsVariantEnumKindOfString().get().asString());
        });
        assertEquals(49, flyweight.constrainedMap().length());
        assertEquals(4, flyweight.constrainedMap().fieldCount());
        assertEquals(4, mapItems.size());
        assertEquals("entry1Key", mapItems.get(0));
        assertEquals("entry1Value", mapItems.get(1));
        assertEquals("entry2Key", mapItems.get(2));
        assertEquals("entry2Value", mapItems.get(3));
    }

    static void assertAllTestValuesReadWithIntValues(
        ListWithConstrainedMapFW flyweight,
        int offset)
    {
        assertEquals(33, flyweight.length());
        assertEquals(1, flyweight.fieldCount());
        assertEquals(offset + 35, flyweight.limit());
        List<String> mapKeys = new ArrayList<>();
        List<Integer> mapValues = new ArrayList<>();
        flyweight.constrainedMap().forEach((k, v) ->
        {
            mapKeys.add(k.get().asString());
            mapValues.add(v.getAsVariantOfInt32().get());
        });
        assertEquals(30, flyweight.constrainedMap().length());
        assertEquals(4, flyweight.constrainedMap().fieldCount());
        assertEquals(2, mapKeys.size());
        assertEquals(2, mapValues.size());
        assertEquals("entry1Key", mapKeys.get(0));
        assertEquals(100, (int) mapValues.get(0));
        assertEquals("entry2Key", mapKeys.get(1));
        assertEquals(1000, (int) mapValues.get(1));
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setStringEntries(buffer, 10);
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
        int length = setStringEntries(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setStringEntries(buffer, 10);

        final ListWithConstrainedMapFW listWithTypedefMap = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, listWithTypedefMap);
        assertAllTestValuesReadWithStringValues(listWithTypedefMap, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setStringEntries(buffer, 10);

        final ListWithConstrainedMapFW listWithTypedefMap = flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(listWithTypedefMap);
        assertSame(flyweightRO, listWithTypedefMap);
        assertAllTestValuesReadWithStringValues(listWithTypedefMap, 10);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSameFieldIsSetMoreThanOnce() throws Exception
    {
        flyweightRW.wrap(buffer, 0, buffer.capacity())
            .constrainedMap(asConstrainedMapFWWithStringValue(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(asStringFW("entry1Value"), asStringFW("entry2Value"))))
            .constrainedMap(asConstrainedMapFWWithStringValue(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(asStringFW("entry1Value"), asStringFW("entry2Value"))))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertErrorWhenValueNotPresent() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .build()
            .limit();

        final ListWithConstrainedMapFW listWithTypedefMap = flyweightRO.wrap(buffer, 0, limit);

        listWithTypedefMap.constrainedMap();
    }

    @Test
    public void shouldSetEntriesWithStringKeyAndStringValue() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .constrainedMap(asConstrainedMapFWWithStringValue(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(asStringFW("entry1Value"), asStringFW("entry2Value"))))
            .build()
            .limit();

        final ListWithConstrainedMapFW listWithTypedefMap = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadWithStringValues(listWithTypedefMap, 0);
    }

    @Test
    public void shouldSetEntriesWithStringKeyAndIntValue() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .constrainedMap(asMapFWWithIntValue(Arrays.asList(asStringFW("entry1Key"), asStringFW("entry2Key")),
                Arrays.asList(100, 1000)))
            .build()
            .limit();

        final ListWithConstrainedMapFW listWithTypedefMap = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadWithIntValues(listWithTypedefMap, 0);
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

    private static VariantEnumKindOfStringFW asVariantEnumKindOfStringFW(
        StringFW value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + value.sizeof()));
        return new VariantEnumKindOfStringFW.Builder().wrap(buffer, 0, buffer.capacity())
            .set(value).build();
    }

    private static VariantOfInt32FW asVariantOfInt32FW(
        int value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + Integer.BYTES));
        return new VariantOfInt32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value).build();
    }

    private static ConstrainedMapFW<VariantWithoutOfFW> asConstrainedMapFWWithStringValue(
        List<StringFW> keys,
        List<StringFW> values)
    {
        ConstrainedMapFW.Builder<VariantWithoutOfFW, VariantWithoutOfFW.Builder> constrainedMapRW =
            new ConstrainedMapFW.Builder<>(new VariantEnumKindOfStringFW(), new VariantWithoutOfFW(),
                new VariantEnumKindOfStringFW.Builder(), new VariantWithoutOfFW.Builder());
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100));
        constrainedMapRW.wrap(buffer, 0, buffer.capacity());
        for (int i = 0; i < keys.size(); i++)
        {
            StringFW key = keys.get(i);
            StringFW value = values.get(i);
            constrainedMapRW.entry(k -> k.set(key),
                v -> v.setAsVariantEnumKindOfString(asVariantEnumKindOfStringFW(value)));
        }
        return constrainedMapRW.build();
    }

    private static ConstrainedMapFW<VariantWithoutOfFW> asMapFWWithIntValue(
        List<StringFW> keys,
        List<Integer> values)
    {
        ConstrainedMapFW.Builder<VariantWithoutOfFW, VariantWithoutOfFW.Builder> constrainedMapRW =
            new ConstrainedMapFW.Builder<>(new VariantEnumKindOfStringFW(), new VariantWithoutOfFW(),
                new VariantEnumKindOfStringFW.Builder(), new VariantWithoutOfFW.Builder());
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100));
        constrainedMapRW.wrap(buffer, 0, buffer.capacity());
        for (int i = 0; i < keys.size(); i++)
        {
            StringFW key = keys.get(i);
            int value = values.get(i);
            constrainedMapRW.entry(k -> k.set(key), v -> v.setAsVariantOfInt32(asVariantOfInt32FW(value)));
        }
        return constrainedMapRW.build();
    }
}
