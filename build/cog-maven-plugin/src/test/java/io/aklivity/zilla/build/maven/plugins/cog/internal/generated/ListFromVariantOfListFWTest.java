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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint32;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.ListFromVariantOfListFW;

public class ListFromVariantOfListFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final ListFromVariantOfListFW.Builder listFromVariantOfListRW = new ListFromVariantOfListFW.Builder();
    private final ListFromVariantOfListFW listFromVariantOfListRO = new ListFromVariantOfListFW();
    private final int lengthSize = Integer.BYTES;
    private final int fieldCountSize = Integer.BYTES;
    private static final EnumWithInt8 KIND_STRING8 = EnumWithInt8.NINE;

    private void setAllFields(
        MutableDirectBuffer buffer)
    {
        int length = 35;
        int fieldCount = 4;
        int offsetKind = 10;
        buffer.putByte(offsetKind, (byte) 1);
        int offsetLength = offsetKind + Byte.BYTES;
        buffer.putInt(offsetLength, length);
        int offsetFieldCount = offsetLength + lengthSize;
        buffer.putInt(offsetFieldCount, fieldCount);

        int offsetVariantOfString1Kind = offsetFieldCount + fieldCountSize;
        buffer.putByte(offsetVariantOfString1Kind, KIND_STRING8.value());
        int offsetVariantOfString1Length = offsetVariantOfString1Kind + Byte.BYTES;
        buffer.putByte(offsetVariantOfString1Length, (byte) "string1".length());
        int offsetVariantOfString1 = offsetVariantOfString1Length + Byte.BYTES;
        buffer.putBytes(offsetVariantOfString1, "string1".getBytes());

        int offsetVariantOfString2Kind = offsetVariantOfString1 + "string1".length();
        buffer.putByte(offsetVariantOfString2Kind, KIND_STRING8.value());
        int offsetVariantOfString2Length = offsetVariantOfString2Kind + Byte.BYTES;
        buffer.putByte(offsetVariantOfString2Length, (byte) "string2".length());
        int offsetVariantOfString2 = offsetVariantOfString2Length + Byte.BYTES;
        buffer.putBytes(offsetVariantOfString2, "string2".getBytes());

        int offsetVariantOfUintKind = offsetVariantOfString2 + "string2".length();
        buffer.putInt(offsetVariantOfUintKind, (int) EnumWithUint32.NI.value());
        int offsetVariantOfUint = offsetVariantOfUintKind + Integer.BYTES;
        buffer.putInt(offsetVariantOfUint, (int) 4000000000L);

        int offsetVariantOfIntKind = offsetVariantOfUint + Integer.BYTES;
        buffer.putByte(offsetVariantOfIntKind, EnumWithInt8.THREE.value());
        int offsetVariantOfInt = offsetVariantOfIntKind + Byte.BYTES;
        buffer.putInt(offsetVariantOfInt, -2000000000);
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = 35;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            try
            {
                listFromVariantOfListRO.wrap(buffer, 10, maxLimit);
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
        int length = 35;
        int offsetLength = 10;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            assertNull(listFromVariantOfListRO.tryWrap(buffer,  offsetLength, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 35;
        int kindSize = Byte.BYTES;
        int lengthSize = Integer.BYTES;
        int fieldCount = 4;
        int offsetLength = 10;
        int maxLimit = offsetLength + kindSize + lengthSize + length;
        setAllFields(buffer);

        final ListFromVariantOfListFW listFromVariantOfList =
            listFromVariantOfListRO.wrap(buffer, offsetLength, maxLimit);

        assertSame(listFromVariantOfListRO, listFromVariantOfList);
        assertEquals(length, listFromVariantOfList.length());
        assertEquals(fieldCount, listFromVariantOfList.fieldCount());
        assertEquals("string1", listFromVariantOfList.variantOfString1().asString());
        assertEquals("string2", listFromVariantOfList.variantOfString2().asString());
        assertEquals(4000000000L, listFromVariantOfList.variantOfUint());
        assertEquals(-2000000000, listFromVariantOfList.variantOfInt());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 35;
        int kindSize = Byte.BYTES;
        int lengthSize = Integer.BYTES;
        int fieldCount = 4;
        int offsetLength = 10;
        int maxLimit = offsetLength + kindSize + lengthSize + length;
        setAllFields(buffer);

        final ListFromVariantOfListFW listFromVariantOfList =
            listFromVariantOfListRO.tryWrap(buffer, offsetLength, maxLimit);

        assertSame(listFromVariantOfListRO, listFromVariantOfList);
        assertEquals(length, listFromVariantOfList.length());
        assertEquals(fieldCount, listFromVariantOfList.fieldCount());
        assertEquals("string1", listFromVariantOfList.variantOfString1().asString());
        assertEquals("string2", listFromVariantOfList.variantOfString2().asString());
        assertEquals(4000000000L, listFromVariantOfList.variantOfUint());
        assertEquals(-2000000000, listFromVariantOfList.variantOfInt());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpace() throws Exception
    {
        listFromVariantOfListRW.wrap(buffer, 10, 17)
            .variantOfString1(asStringFW("string1"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenFieldIsSetOutOfOrder() throws Exception
    {
        listFromVariantOfListRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .variantOfUint(4000000000L)
            .variantOfString2(asStringFW("string2"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSameFieldIsSetMoreThanOnce() throws Exception
    {
        listFromVariantOfListRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .variantOfString1(asStringFW("string2"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenRequiredFieldIsNotSet() throws Exception
    {
        listFromVariantOfListRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString2(asStringFW("string2"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertErrorWhenValueNotPresent() throws Exception
    {
        int limit = listFromVariantOfListRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .build()
            .limit();

        final ListFromVariantOfListFW listFromVariantOfList = listFromVariantOfListRO.wrap(buffer, 0, limit);

        assertEquals("string2", listFromVariantOfList.variantOfString2().asString());
    }

    @Test
    public void shouldSetOnlyRequiredFields() throws Exception
    {
        int limit = listFromVariantOfListRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .build()
            .limit();

        final ListFromVariantOfListFW listFromVariantOfList = listFromVariantOfListRO.wrap(buffer, 0, limit);

        assertEquals("string1", listFromVariantOfList.variantOfString1().asString());
        assertEquals(4000000000L, listFromVariantOfList.variantOfUint());
    }

    @Test
    public void shouldSetSomeFields() throws Exception
    {
        int limit = listFromVariantOfListRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .variantOfUint(4000000000L)
            .build()
            .limit();

        final ListFromVariantOfListFW listFromVariantOfList = listFromVariantOfListRO.wrap(buffer, 0, limit);

        assertEquals("string1", listFromVariantOfList.variantOfString1().asString());
        assertEquals(4000000000L, listFromVariantOfList.variantOfUint());
    }

    @Test
    public void shouldSetAllFields() throws Exception
    {
        int limit = listFromVariantOfListRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .variantOfString2(asStringFW("string2"))
            .variantOfUint(4000000000L)
            .variantOfInt(-2000000000)
            .build()
            .limit();

        final ListFromVariantOfListFW listFromVariantOfList = listFromVariantOfListRO.wrap(buffer, 0, limit);

        assertEquals("string1", listFromVariantOfList.variantOfString1().asString());
        assertEquals("string2", listFromVariantOfList.variantOfString2().asString());
        assertEquals(4000000000L, listFromVariantOfList.variantOfUint());
        assertEquals(-2000000000, listFromVariantOfList.variantOfInt());
    }

    private static StringFW asStringFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
