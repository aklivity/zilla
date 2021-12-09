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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.Array16FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String16FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantEnumKindOfStringFW;

public class Array16FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(150000))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final Array16FW<VariantEnumKindOfStringFW> flyweightRO =
        new Array16FW<>(new VariantEnumKindOfStringFW());

    private final int lengthSize = Short.BYTES;
    private final int fieldCountSize = Short.BYTES;
    private final int arrayItemKindSize = Byte.BYTES;

    private int setVariantItems(
        MutableDirectBuffer buffer,
        int offset)
    {
        String item1 = String.format("%1000s", "0");
        String item2 = String.format("%1000s", "1");
        int itemLengthSize = Short.BYTES;
        int length = fieldCountSize + arrayItemKindSize + itemLengthSize + item1.length() + itemLengthSize +
            item2.length();
        int fieldCount = 2;
        buffer.putShort(offset, (short) length);
        int offsetFieldCount = offset + lengthSize;
        buffer.putShort(offsetFieldCount, (short) fieldCount);

        int offsetArrayItemKind = offsetFieldCount + fieldCountSize;
        buffer.putByte(offsetArrayItemKind, EnumWithInt8.TEN.value());

        int offsetItem1Length = offsetArrayItemKind + Byte.BYTES;
        buffer.putShort(offsetItem1Length, (short) item1.length());
        int offsetItem1 = offsetItem1Length + itemLengthSize;
        buffer.putBytes(offsetItem1, item1.getBytes());

        int offsetItem2Length = offsetItem1 + item1.length();
        buffer.putShort(offsetItem2Length, (short) item2.length());
        int offsetItem2 = offsetItem2Length + itemLengthSize;
        buffer.putBytes(offsetItem2, item2.getBytes());

        return length + lengthSize;
    }

    static void assertAllTestValuesReadCaseVariantItems(
        Array16FW<VariantEnumKindOfStringFW> flyweight,
        int offset)
    {
        String item1 = String.format("%1000s", "0");
        String item2 = String.format("%1000s", "1");
        List<String> arrayItems = new ArrayList<>();
        flyweight.forEach(v -> arrayItems.add(v.get().asString()));
        assertEquals(2, arrayItems.size());
        assertEquals(item1, arrayItems.get(0));
        assertEquals(item2, arrayItems.get(1));
        assertEquals(2007, flyweight.length());
        assertEquals(2, flyweight.fieldCount());
        assertEquals(offset + 2009, flyweight.limit());
    }

    static void assertAllTestValuesReadCaseNonVariantItems(
        Array16FW<String16FW> flyweight,
        int offset)
    {
        String item1 = String.format("%1000s", "0");
        String item2 = String.format("%1000s", "1");
        List<String> arrayItems = new ArrayList<>();
        flyweight.forEach(v -> arrayItems.add(v.asString()));
        assertEquals(2, arrayItems.size());
        assertEquals(item1, arrayItems.get(0));
        assertEquals(item2, arrayItems.get(1));
        assertEquals(2006, flyweight.length());
        assertEquals(2, flyweight.fieldCount());
        assertEquals(offset + 2008, flyweight.limit());
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setVariantItems(buffer, 10);
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
        int length = setVariantItems(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setVariantItems(buffer, 10);

        final Array16FW<VariantEnumKindOfStringFW> array16 =
            flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, array16);
        assertAllTestValuesReadCaseVariantItems(array16, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setVariantItems(buffer, 10);

        final Array16FW<VariantEnumKindOfStringFW> array16 =
            flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(array16);
        assertSame(flyweightRO, array16);
        assertAllTestValuesReadCaseVariantItems(array16, 10);
    }

    @Test
    public void shouldSetNonVariantItemsUsingItemMethod() throws Exception
    {
        Array16FW.Builder<String16FW.Builder, String16FW> flyweightRW =
            new Array16FW.Builder<>(new String16FW.Builder(), new String16FW());

        Array16FW<String16FW> flyweightRO = new Array16FW<>(new String16FW());

        String item1 = String.format("%1000s", "0");
        String item2 = String.format("%1000s", "1");

        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .item(b -> b.set(item1, UTF_8))
            .item(b -> b.set(item2, UTF_8))
            .build()
            .limit();

        final Array16FW<String16FW> array = flyweightRO.wrap(buffer,  0,  limit);

        assertAllTestValuesReadCaseNonVariantItems(array, 0);

    }

    @Test
    public void shouldSetVariantItemsUsingItemMethod() throws Exception
    {
        Array16FW.Builder
            <VariantEnumKindOfStringFW.Builder, VariantEnumKindOfStringFW>
            flyweightRW = new Array16FW.Builder<>(new VariantEnumKindOfStringFW.Builder(),
            new VariantEnumKindOfStringFW());

        Array16FW<VariantEnumKindOfStringFW> flyweightRO = new Array16FW<>(new VariantEnumKindOfStringFW());

        String item1 = String.format("%1000s", "0");
        String item2 = String.format("%1000s", "1");

        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .item(b -> b.setAsString32(asStringFW(item1)))
            .item(b -> b.setAsString32(asStringFW(item2)))
            .build()
            .limit();

        final Array16FW<VariantEnumKindOfStringFW> array = flyweightRO.wrap(buffer,  0,  limit);

        assertAllTestValuesReadCaseVariantItems(array, 0);
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
}
