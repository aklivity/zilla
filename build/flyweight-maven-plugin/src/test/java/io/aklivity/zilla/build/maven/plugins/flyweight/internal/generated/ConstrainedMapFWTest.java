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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.ConstrainedMapFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantEnumKindOfStringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantWithoutOfFW;

public class ConstrainedMapFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final ConstrainedMapFW.Builder<VariantWithoutOfFW, VariantWithoutOfFW.Builder> flyweightRW =
        new ConstrainedMapFW.Builder<>(new VariantEnumKindOfStringFW(), new VariantWithoutOfFW(),
            new VariantEnumKindOfStringFW.Builder(), new VariantWithoutOfFW.Builder());
    private final ConstrainedMapFW<VariantWithoutOfFW> flyweightRO =
        new ConstrainedMapFW<>(new VariantEnumKindOfStringFW(), new VariantWithoutOfFW());
    private static final EnumWithInt8 KIND_MAP8 = EnumWithInt8.SIX;
    private final int kindSize = Byte.BYTES;
    private final int lengthSize = Byte.BYTES;
    private final int fieldCountSize = Byte.BYTES;

    private int setTwoEntries(
        MutableDirectBuffer buffer,
        int offset)
    {
        int length = 49;
        int fieldCount = 4;
        String entry1Key = "entry1Key";
        String entry1Value = "entry1Value";
        String entry2Key = "entry2Key";
        String entry2Value = "entry2Value";

        buffer.putByte(offset, KIND_MAP8.value());
        int offsetLength = offset + kindSize;
        buffer.putByte(offsetLength, (byte) length);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putByte(offsetFieldCount, (byte) fieldCount);

        int offsetMapEntry1KeyKind = offsetFieldCount + fieldCountSize;
        buffer.putByte(offsetMapEntry1KeyKind, EnumWithInt8.NINE.value());
        int offsetEntry1KeyLength = offsetMapEntry1KeyKind + Byte.BYTES;
        buffer.putByte(offsetEntry1KeyLength, (byte) entry1Key.length());
        int offsetEntry1Key = offsetEntry1KeyLength + Byte.BYTES;
        buffer.putBytes(offsetEntry1Key, entry1Key.getBytes());

        int offsetMapEntry1ValueKind = offsetEntry1Key + entry1Key.length();
        buffer.putByte(offsetMapEntry1ValueKind, EnumWithInt8.NINE.value());
        int offsetEntry1ValueLength = offsetMapEntry1ValueKind + Byte.BYTES;
        buffer.putByte(offsetEntry1ValueLength, (byte) entry1Value.length());
        int offsetEntry1Value = offsetEntry1ValueLength + Byte.BYTES;
        buffer.putBytes(offsetEntry1Value, entry1Value.getBytes());

        int offsetMapEntry2KeyKind = offsetEntry1Value + entry1Value.length();
        buffer.putByte(offsetMapEntry2KeyKind, EnumWithInt8.NINE.value());
        int offsetEntry2KeyLength = offsetMapEntry2KeyKind + Byte.BYTES;
        buffer.putByte(offsetEntry2KeyLength, (byte) entry2Key.length());
        int offsetEntry2Key = offsetEntry2KeyLength + Byte.BYTES;
        buffer.putBytes(offsetEntry2Key, entry2Key.getBytes());

        int offsetMapEntry2ValueKind = offsetEntry2Key + entry2Key.length();
        buffer.putByte(offsetMapEntry2ValueKind, EnumWithInt8.NINE.value());
        int offsetEntry2ValueLength = offsetMapEntry2ValueKind + Byte.BYTES;
        buffer.putByte(offsetEntry2ValueLength, (byte) entry2Value.length());
        int offsetEntry2Value = offsetEntry2ValueLength + Byte.BYTES;
        buffer.putBytes(offsetEntry2Value, entry2Value.getBytes());

        return length + kindSize + lengthSize;
    }

    static void assertAllTestValuesRead(
        ConstrainedMapFW<VariantWithoutOfFW> flyweight,
        int offset)
    {
        List<String> mapItems = new ArrayList<>();
        flyweight.forEach((k, v) ->
        {
            mapItems.add(k.get().asString());
            mapItems.add(v.getAsVariantEnumKindOfString().get().asString());
        });
        assertEquals(4, mapItems.size());
        assertEquals("entry1Key", mapItems.get(0));
        assertEquals("entry1Value", mapItems.get(1));
        assertEquals("entry2Key", mapItems.get(2));
        assertEquals("entry2Value", mapItems.get(3));
        assertEquals(49, flyweight.length());
        assertEquals(4, flyweight.fieldCount());
        assertEquals(offset + 51, flyweight.limit());
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setTwoEntries(buffer, 10);
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
        int length = setTwoEntries(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setTwoEntries(buffer, 10);

        final ConstrainedMapFW<VariantWithoutOfFW> constrainedMap = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, constrainedMap);
        assertAllTestValuesRead(constrainedMap, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setTwoEntries(buffer, 10);

        final ConstrainedMapFW<VariantWithoutOfFW> constrainedMap =
            flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(constrainedMap);
        assertSame(flyweightRO, constrainedMap);
        assertAllTestValuesRead(constrainedMap, 10);
    }

    @Test
    public void shouldWrapAndReadItems() throws Exception
    {
        final int offset = 10;
        int size = setTwoEntries(buffer, offset);
        final ConstrainedMapFW<VariantWithoutOfFW> constrainedMap = flyweightRO.wrap(buffer, offset, buffer.capacity());
        assertEquals(offset + size, constrainedMap.limit());

        assertAllTestValuesRead(constrainedMap, offset);
    }

    @Test
    public void shouldReadEmptyList() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .build()
            .limit();

        final ConstrainedMapFW<VariantWithoutOfFW> constrainedMap = flyweightRO.wrap(buffer, 0, limit);

        List<String> mapItems = new ArrayList<>();
        constrainedMap.forEach((k, v) ->
        {
            mapItems.add(k.get().asString());
            mapItems.add(v.getAsVariantEnumKindOfString().get().asString());
        });

        assertEquals(3, constrainedMap.limit());
        assertEquals(0, constrainedMap.fieldCount());
        assertEquals(1, constrainedMap.length());
        assertEquals(0, mapItems.size());
    }

    @Test
    public void shouldSetEntries() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .entry(k -> k.set(asStringFW("entry1Key")),
                v -> v.setAsVariantEnumKindOfString(asVariantEnumKindOfStringFW(asStringFW("entry1Value"))))
            .entry(k -> k.set(asStringFW("entry2Key")),
                v -> v.setAsVariantEnumKindOfString(asVariantEnumKindOfStringFW(asStringFW("entry2Value"))))
            .build()
            .limit();

        final ConstrainedMapFW<VariantWithoutOfFW> constrainedMap = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(constrainedMap, 0);
    }

    private static VariantEnumKindOfStringFW asVariantEnumKindOfStringFW(
        StringFW value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + value.sizeof()));
        return new VariantEnumKindOfStringFW.Builder().wrap(buffer, 0, buffer.capacity())
            .set(value).build();
    }

    private static StringFW asStringFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
