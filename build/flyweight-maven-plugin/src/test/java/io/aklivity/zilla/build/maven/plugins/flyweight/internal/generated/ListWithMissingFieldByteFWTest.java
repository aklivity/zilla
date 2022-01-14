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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.StringFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint32;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.ListWithMissingFieldByteFW;

public class ListWithMissingFieldByteFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final ListWithMissingFieldByteFW.Builder listWithMissingFieldByteRW = new ListWithMissingFieldByteFW.Builder();
    private final ListWithMissingFieldByteFW listWithMissingFieldByteRO = new ListWithMissingFieldByteFW();
    private final int lengthSize = Integer.BYTES;
    private final int fieldCountSize = Integer.BYTES;
    private static final EnumWithInt8 KIND_STRING8 = EnumWithInt8.NINE;

    private void setAllFields(
        MutableDirectBuffer buffer)
    {
        int length = 39;
        int fieldCount = 4;
        int offsetLength = 10;
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
        int length = 39;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            try
            {
                listWithMissingFieldByteRO.wrap(buffer,  10, maxLimit);
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
        int length = 39;
        int offsetLength = 10;
        setAllFields(buffer);
        for (int maxLimit = 10; maxLimit <= length; maxLimit++)
        {
            assertNull(listWithMissingFieldByteRO.tryWrap(buffer,  offsetLength, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 39;
        int fieldCount = 4;
        int offsetLength = 10;
        setAllFields(buffer);

        assertSame(listWithMissingFieldByteRO, listWithMissingFieldByteRO.wrap(buffer, offsetLength,
            offsetLength + length));
        assertEquals(length, listWithMissingFieldByteRO.limit() - offsetLength);
        assertEquals(fieldCount, listWithMissingFieldByteRO.fieldCount());
        assertEquals("string1", listWithMissingFieldByteRO.variantOfString1().asString());
        assertEquals("string2", listWithMissingFieldByteRO.variantOfString2().asString());
        assertEquals(4000000000L, listWithMissingFieldByteRO.variantOfUint());
        assertEquals(-2000000000, listWithMissingFieldByteRO.variantOfInt());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 39;
        int fieldCount = 4;
        int offsetLength = 10;
        setAllFields(buffer);

        assertSame(listWithMissingFieldByteRO, listWithMissingFieldByteRO.tryWrap(buffer, offsetLength,
            offsetLength + length));
        assertEquals(length, listWithMissingFieldByteRO.limit() - offsetLength);
        assertEquals(fieldCount, listWithMissingFieldByteRO.fieldCount());
        assertEquals("string1", listWithMissingFieldByteRO.variantOfString1().asString());
        assertEquals("string2", listWithMissingFieldByteRO.variantOfString2().asString());
        assertEquals(4000000000L, listWithMissingFieldByteRO.variantOfUint());
        assertEquals(-2000000000, listWithMissingFieldByteRO.variantOfInt());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpace() throws Exception
    {
        listWithMissingFieldByteRW.wrap(buffer, 10, 17)
            .variantOfString1(asStringFW("string1"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenFieldIsSetOutOfOrder() throws Exception
    {
        listWithMissingFieldByteRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .variantOfUint(4000000000L)
            .variantOfString2(asStringFW("string2"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSameFieldIsSetMoreThanOnce() throws Exception
    {
        listWithMissingFieldByteRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .variantOfString1(asStringFW("string2"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenRequiredFieldIsNotSet() throws Exception
    {
        listWithMissingFieldByteRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString2(asStringFW("string2"))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertErrorWhenValueNotPresent() throws Exception
    {
        int limit = listWithMissingFieldByteRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .build()
            .limit();
        listWithMissingFieldByteRO.wrap(buffer,  0,  limit);
        assertEquals("string2", listWithMissingFieldByteRO.variantOfString2().asString());
    }

    @Test
    public void shouldSetOnlyRequiredFields() throws Exception
    {
        int limit = listWithMissingFieldByteRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .build()
            .limit();
        listWithMissingFieldByteRO.wrap(buffer,  0,  limit);
        assertEquals("string1", listWithMissingFieldByteRO.variantOfString1().asString());
        assertEquals(4000000000L, listWithMissingFieldByteRO.variantOfUint());
    }

    @Test
    public void shouldSetSomeFields() throws Exception
    {
        int limit = listWithMissingFieldByteRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .variantOfUint(4000000000L)
            .build()
            .limit();
        listWithMissingFieldByteRO.wrap(buffer,  0,  limit);
        assertEquals("string1", listWithMissingFieldByteRO.variantOfString1().asString());
        assertEquals(4000000000L, listWithMissingFieldByteRO.variantOfUint());
    }

    @Test
    public void shouldSetAllFields() throws Exception
    {
        int limit = listWithMissingFieldByteRW.wrap(buffer, 0, buffer.capacity())
            .variantOfString1(asStringFW("string1"))
            .variantOfString2(asStringFW("string2"))
            .variantOfUint(4000000000L)
            .variantOfInt(-2000000000)
            .build()
            .limit();
        listWithMissingFieldByteRO.wrap(buffer,  0,  limit);
        assertEquals("string1", listWithMissingFieldByteRO.variantOfString1().asString());
        assertEquals("string2", listWithMissingFieldByteRO.variantOfString2().asString());
        assertEquals(4000000000L, listWithMissingFieldByteRO.variantOfUint());
        assertEquals(-2000000000, listWithMissingFieldByteRO.variantOfInt());
    }

    private static StringFW asStringFW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
