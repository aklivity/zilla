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
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.Array32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String32FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantEnumKindOfStringFW;

public class Array32FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(150000))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final Array32FW<VariantEnumKindOfStringFW> flyweightRO =
        new Array32FW<>(new VariantEnumKindOfStringFW());

    private final int lengthSize = Integer.BYTES;
    private final int fieldCountSize = Integer.BYTES;
    private final int arrayItemKindSize = Byte.BYTES;

    private int setVariantItems(
        MutableDirectBuffer buffer,
        int offset)
    {
        String item1 = String.format("%65535s", "0");
        String item2 = String.format("%65535s", "1");
        int itemLengthSize = Integer.BYTES;
        int length = fieldCountSize + arrayItemKindSize + itemLengthSize + item1.length() + itemLengthSize +
            item2.length();
        int fieldCount = 2;
        buffer.putInt(offset, length);
        int offsetFieldCount = offset + lengthSize;
        buffer.putInt(offsetFieldCount, fieldCount);

        int offsetArrayItemKind = offsetFieldCount + fieldCountSize;
        buffer.putByte(offsetArrayItemKind, EnumWithInt8.ELEVEN.value());

        int offsetItem1Length = offsetArrayItemKind + Byte.BYTES;
        buffer.putInt(offsetItem1Length, item1.length());
        int offsetItem1 = offsetItem1Length + itemLengthSize;
        buffer.putBytes(offsetItem1, item1.getBytes());

        int offsetItem2Length = offsetItem1 + item1.length();
        buffer.putInt(offsetItem2Length, item2.length());
        int offsetItem2 = offsetItem2Length + itemLengthSize;
        buffer.putBytes(offsetItem2, item2.getBytes());

        return length + lengthSize;
    }

    static void assertAllTestValuesReadCaseVariantItems(
        Array32FW<VariantEnumKindOfStringFW> flyweight,
        int offset)
    {
        String item1 = String.format("%65535s", "0");
        String item2 = String.format("%65535s", "1");
        List<String> arrayItems = new ArrayList<>();
        flyweight.forEach(v -> arrayItems.add(v.get().asString()));
        assertEquals(2, arrayItems.size());
        assertEquals(item1, arrayItems.get(0));
        assertEquals(item2, arrayItems.get(1));
        assertEquals(131083, flyweight.length());
        assertEquals(2, flyweight.fieldCount());
        assertEquals(offset + 131087, flyweight.limit());
    }

    static void assertAllTestValuesReadCaseNonVariantItems(
        Array32FW<String32FW> flyweight,
        int offset)
    {
        String item1 = String.format("%65535s", "0");
        String item2 = String.format("%65535s", "1");
        List<String> arrayItems = new ArrayList<>();
        flyweight.forEach(v -> arrayItems.add(v.asString()));
        assertEquals(2, arrayItems.size());
        assertEquals(item1, arrayItems.get(0));
        assertEquals(item2, arrayItems.get(1));
        assertEquals(131082, flyweight.length());
        assertEquals(2, flyweight.fieldCount());
        assertEquals(offset + 131086, flyweight.limit());
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

        final Array32FW<VariantEnumKindOfStringFW> array32 =
            flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, array32);
        assertAllTestValuesReadCaseVariantItems(array32, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setVariantItems(buffer, 10);

        final Array32FW<VariantEnumKindOfStringFW> array32 =
            flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(array32);
        assertSame(flyweightRO, array32);
        assertAllTestValuesReadCaseVariantItems(array32, 10);
    }


    @Test
    public void shouldSetNonVariantItemsUsingItemMethod() throws Exception
    {
        Array32FW.Builder<String32FW.Builder, String32FW> flyweightRW =
            new Array32FW.Builder<>(new String32FW.Builder(), new String32FW());

        Array32FW<String32FW> flyweightRO = new Array32FW<>(new String32FW());

        String item1 = String.format("%65535s", "0");
        String item2 = String.format("%65535s", "1");

        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .item(b -> b.set(item1, UTF_8))
            .item(b -> b.set(item2, UTF_8))
            .build()
            .limit();

        final Array32FW<String32FW> array = flyweightRO.wrap(buffer,  0,  limit);

        assertAllTestValuesReadCaseNonVariantItems(array, 0);

    }

    @Test
    public void shouldSetVariantItemsUsingItemMethod() throws Exception
    {
        Array32FW.Builder<VariantEnumKindOfStringFW.Builder, VariantEnumKindOfStringFW> flyweightRW =
            new Array32FW.Builder<>(
                new VariantEnumKindOfStringFW.Builder(),
                new VariantEnumKindOfStringFW());

        Array32FW<VariantEnumKindOfStringFW> flyweightRO = new Array32FW<>(new VariantEnumKindOfStringFW());

        String item1 = String.format("%65535s", "0");
        String item2 = String.format("%65535s", "1");

        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .item(b -> b.setAsString32(asStringFW(item1)))
            .item(b -> b.setAsString32(asStringFW(item2)))
            .build()
            .limit();

        final Array32FW<VariantEnumKindOfStringFW> array = flyweightRO.wrap(buffer,  0,  limit);

        assertAllTestValuesReadCaseVariantItems(array, 0);
    }

    private static StringFW asStringFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Integer.BYTES + value.length()));
        return new String32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
