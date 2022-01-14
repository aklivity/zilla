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

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.Array8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantEnumKindOfStringFW;

public class Array8FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final Array8FW<VariantEnumKindOfStringFW> flyweightRO =
        new Array8FW<>(new VariantEnumKindOfStringFW());

    private final int lengthSize = Byte.BYTES;
    private final int fieldCountSize = Byte.BYTES;

    private int setVariantItems(
        MutableDirectBuffer buffer,
        int offset)
    {
        int length = 18;
        int fieldCount = 2;
        buffer.putByte(offset, (byte) length);
        int offsetFieldCount = offset + lengthSize;
        buffer.putByte(offsetFieldCount, (byte) fieldCount);

        int offsetArrayItemKind = offsetFieldCount + fieldCountSize;
        buffer.putByte(offsetArrayItemKind, EnumWithInt8.NINE.value());

        int offsetItem1Length = offsetArrayItemKind + Byte.BYTES;
        buffer.putByte(offsetItem1Length, (byte) "symbolA".length());
        int offsetItem1 = offsetItem1Length + Byte.BYTES;
        buffer.putBytes(offsetItem1, "symbolA".getBytes());

        int offsetItem2Length = offsetItem1 + "symbolA".length();
        buffer.putByte(offsetItem2Length, (byte) "symbolB".length());
        int offsetItem2 = offsetItem2Length + Byte.BYTES;
        buffer.putBytes(offsetItem2, "symbolB".getBytes());

        return length + lengthSize;
    }

    static void assertAllTestValuesReadCaseVariantItems(
        Array8FW<VariantEnumKindOfStringFW> flyweight,
        int offset)
    {
        List<String> arrayItems = new ArrayList<>();
        flyweight.forEach(v -> arrayItems.add(v.get().asString()));
        assertEquals(2, arrayItems.size());
        assertEquals("symbolA", arrayItems.get(0));
        assertEquals("symbolB", arrayItems.get(1));
        assertEquals(18, flyweight.length());
        assertEquals(2, flyweight.fieldCount());
        assertEquals(offset + 19, flyweight.limit());
    }

    static void assertAllTestValuesReadCaseNonVariantItems(
        Array8FW<String8FW> flyweight,
        int offset)
    {
        List<String> arrayItems = new ArrayList<>();
        flyweight.forEach(v -> arrayItems.add(v.asString()));
        assertEquals(2, arrayItems.size());
        assertEquals("symbolA", arrayItems.get(0));
        assertEquals("symbolB", arrayItems.get(1));
        assertEquals(17, flyweight.length());
        assertEquals(2, flyweight.fieldCount());
        assertEquals(offset + 18, flyweight.limit());
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

        final Array8FW<VariantEnumKindOfStringFW> array8 =
            flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, array8);
        assertAllTestValuesReadCaseVariantItems(array8, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setVariantItems(buffer, 10);

        final Array8FW<VariantEnumKindOfStringFW> array8 =
            flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(array8);
        assertSame(flyweightRO, array8);
        assertAllTestValuesReadCaseVariantItems(array8, 10);
    }

    @Test
    public void shouldSetNonVariantItemsUsingItemMethod() throws Exception
    {
        Array8FW.Builder<String8FW.Builder, String8FW> flyweightRW =
            new Array8FW.Builder<>(new String8FW.Builder(), new String8FW());

        Array8FW<String8FW> flyweightRO = new Array8FW<>(new String8FW());

        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .item(b -> b.set("symbolA", UTF_8))
            .item(b -> b.set("symbolB", UTF_8))
            .build()
            .limit();

        final Array8FW<String8FW> array = flyweightRO.wrap(buffer,  0,  limit);

        assertAllTestValuesReadCaseNonVariantItems(array, 0);

    }

    @Test
    public void shouldSetVariantItemsUsingItemMethod() throws Exception
    {
        Array8FW.Builder<VariantEnumKindOfStringFW.Builder, VariantEnumKindOfStringFW> flyweightRW =
            new Array8FW.Builder<>(
                new VariantEnumKindOfStringFW.Builder(),
                new VariantEnumKindOfStringFW());

        Array8FW<VariantEnumKindOfStringFW> flyweightRO = new Array8FW<>(new VariantEnumKindOfStringFW());

        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .item(b -> b.setAsString32(asStringFW("symbolA")))
            .item(b -> b.setAsString32(asStringFW("symbolB")))
            .build()
            .limit();

        final Array8FW<VariantEnumKindOfStringFW> array = flyweightRO.wrap(buffer,  0,  limit);

        assertAllTestValuesReadCaseVariantItems(array, 0);
    }

    private static StringFW asStringFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.BYTES + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
