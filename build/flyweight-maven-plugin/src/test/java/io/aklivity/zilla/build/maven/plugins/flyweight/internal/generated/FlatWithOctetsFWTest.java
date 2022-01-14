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

import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generated.FlyweightTest.putMediumInt;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.OctetsFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.String8FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.FlatWithOctetsFW;

public class FlatWithOctetsFWTest
{
    private static final int MEDIUM_INT_BYTES = 3;

    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final FlatWithOctetsFW.Builder flatWithOctetsRW = new FlatWithOctetsFW.Builder();
    private final FlatWithOctetsFW flatWithOctetsRO = new FlatWithOctetsFW();

    @Test
    public void shouldNotTryWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int offsetLengthOctets2 = Integer.BYTES + 10;
        buffer.putShort(10 + offsetLengthOctets2, (short) 0);
        int offsetString1 = offsetLengthOctets2 + Short.BYTES;
        buffer.putByte(10 + offsetString1, (byte) 0);
        int offsetLengthOctets3 = offsetString1 + Byte.BYTES;
        buffer.putByte(10 + offsetLengthOctets3, (byte) 0);
        int offsetLengthOctets4 = offsetLengthOctets3 + Byte.BYTES;
        buffer.putInt(10 + offsetLengthOctets4, 0);
        for (int maxLimit = 31; maxLimit < 10 + offsetLengthOctets4 + Integer.BYTES; maxLimit++)
        {
            assertNull(flatWithOctetsRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int offsetLengthOctets2 = Integer.BYTES + 10;
        buffer.putShort(10 + offsetLengthOctets2, (short) 0);
        int offsetString1 = offsetLengthOctets2 + Short.BYTES;
        buffer.putByte(10 + offsetString1, (byte) 0);
        int offsetLengthOctets3 = offsetString1 + Byte.BYTES;
        buffer.putByte(10 + offsetLengthOctets3, (byte) 0);
        int offsetLengthOctets4 = offsetLengthOctets3 + Byte.BYTES;
        buffer.putInt(10 + offsetLengthOctets4, 0);
        for (int maxLimit = 10; maxLimit < 10 + offsetLengthOctets4 + Integer.BYTES; maxLimit++)
        {
            try
            {
                flatWithOctetsRO.wrap(buffer,  10, maxLimit);
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
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int offsetLengthOctets2 = Integer.BYTES + 10;
        buffer.putShort(10 + offsetLengthOctets2, (short) 0);
        int offsetString1 = offsetLengthOctets2 + Short.BYTES;
        buffer.putByte(10 + offsetString1, (byte) 0);
        int offsetLengthOctets3 = offsetString1 + Byte.BYTES;
        buffer.putByte(10 + offsetLengthOctets3, (byte) 0);
        int offsetLengthOctets4 = offsetLengthOctets3 + Byte.BYTES;
        buffer.putInt(10 + offsetLengthOctets4, 0);
        int offsetLengthOctets5 = offsetLengthOctets4 + Integer.BYTES;
        putMediumInt(buffer, 10 + offsetLengthOctets5, 0);
        assertSame(flatWithOctetsRO, flatWithOctetsRO.tryWrap(buffer, 10, 10 + offsetLengthOctets5 + MEDIUM_INT_BYTES));
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int offsetLengthOctets2 = Integer.BYTES + 10;
        buffer.putShort(10 + offsetLengthOctets2, (short) 0);
        int offsetString1 = offsetLengthOctets2 + Short.BYTES;
        buffer.putByte(10 + offsetString1, (byte) 0);
        int offsetLengthOctets3 = offsetString1 + Byte.BYTES;
        buffer.putByte(10 + offsetLengthOctets3, (byte) 0);
        int offsetLengthOctets4 = offsetLengthOctets3 + Byte.BYTES;
        buffer.putInt(10 + offsetLengthOctets4, 0);
        int offsetLengthOctets5 = offsetLengthOctets4 + Integer.BYTES;
        putMediumInt(buffer, 10 + offsetLengthOctets5, 0);
        assertSame(flatWithOctetsRO, flatWithOctetsRO.wrap(buffer, 10, 10 + offsetLengthOctets5 + MEDIUM_INT_BYTES));
    }

    @Test
    public void shouldDefaultValues() throws Exception
    {
        int limit = flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
                .string1("value1")
                .octets2(b -> b.put("12345678901".getBytes(UTF_8)))
                .build()
                .limit();
        flatWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(11, flatWithOctetsRO.fixed1());
        assertNull(flatWithOctetsRO.octets3());
    }

    @Test
    public void shouldExplicitlySetOctetsValuesWithNullDefaultToNull() throws Exception
    {
        int limit = flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .octets1(asOctetsFW("1234567890"))
                .string1("value1")
                .octets2(asOctetsFW("12345678901"))
                .lengthOctets3(-1)
                .octets3((OctetsFW) null)
                .octets4((OctetsFW) null)
                .build()
                .limit();
        flatWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(11, flatWithOctetsRO.fixed1());
        assertNull(flatWithOctetsRO.octets3());
    }

    @Test
    public void shouldAutomaticallySetLength() throws Exception
    {
        int limit = flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
                .string1("value1")
                .octets2(b -> b.put("123456".getBytes(UTF_8)))
                .build()
                .limit();
        flatWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, flatWithOctetsRO.lengthOctets2());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetFixed1WithInsufficientSpace()
    {
        flatWithOctetsRW.wrap(buffer, 10, 11)
               .fixed1(10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpaceToDefaultPriorField()
    {
        flatWithOctetsRW.wrap(buffer, 10, 11)
                .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
                .string1("");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WithInsufficientSpace()
    {
        flatWithOctetsRW.wrap(buffer, 10, 18)
                .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
                .string1("1234");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetOctets1WithInsufficientSpace()
    {
        flatWithOctetsRW.wrap(buffer, 10, 16)
                .octets1(b -> b.put("1234567890".getBytes(UTF_8)));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetOctets1WithValueLongerThanSize()
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
                .octets1(b -> b.put("12345678901".getBytes(UTF_8)));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToSetOctets1WithValueShorterThanSize()
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
                .octets1(b -> b.put("123456789".getBytes(UTF_8)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetOctets1WithValueLongerThanSizeUsingBuffer()
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
                .octets1(asBuffer("12345678901"), 0, 11);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetOctets1WithValueShorterThanSizeUsingBuffer()
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
                .fixed1(0)
                .octets1(asBuffer("123456789"), 0, 9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetOctets2ToNull() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .octets1(asOctetsFW("1234567890"))
                .string1("value1")
                .octets2((OctetsFW) null);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToSetOctets3WithLengthTooLong() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(asOctetsFW("1234567890"))
                .string1("value1")
                .octets2(asOctetsFW("12345"))
                .lengthOctets3(4)  // too long, should be 3
                .octets3(asOctetsFW("678"));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToSetOctets3ToNullWithLengthNotSet() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(asOctetsFW("1234567890"))
                .string1("value1")
                .octets2(asOctetsFW("12345"))
                .octets3((OctetsFW) null);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToSetOctets3WithLengthTooLongVariant1() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(asOctetsFW("1234567890"))
                .string1("value1")
                .octets2(asOctetsFW("12345"))
                .lengthOctets3(4)  // too long, should be 3
                .octets3(b -> b.set("678".getBytes(UTF_8)));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToSetOctets3WithLengthTooLongVariant2() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(asOctetsFW("1234567890"))
                .string1("value1")
                .octets2(asBuffer("12345"), 0, "12345".length())
                .lengthOctets3(4)  // too long, should be 3
                .octets3(asBuffer("678"), 0, "678".length());
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetFixed1() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .fixed1(101)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetString1() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1("value1")
            .string1("another value")
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenOctets1NotSet() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenString1NotSet() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenOctets2NotSet() throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, 100)
            .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
            .string1("value1")
            .build();
    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        int limit = flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
                .string1("value1")
                .octets2(b -> b.put("12345".getBytes(UTF_8)))
                .lengthOctets3(3)
                .octets3(b -> b.put("678".getBytes(UTF_8)))
                .extension(b -> b.put("octetsValue".getBytes(UTF_8)))
                .build()
                .limit();
        flatWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(5, flatWithOctetsRO.fixed1());
        assertEquals("value1", flatWithOctetsRO.string1().asString());
        final String octets3 = flatWithOctetsRO.octets3().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("678", octets3);
        final String extension = flatWithOctetsRO.extension().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("octetsValue", extension);
    }

    @Test
    public void shouldSetAllValuesUsingOctetsFW() throws Exception
    {
        int limit = flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(asOctetsFW("1234567890"))
                .string1("value1")
                .octets2(asOctetsFW("12345"))
                .lengthOctets3(3)
                .octets3(asOctetsFW("678"))
                .extension(asOctetsFW("octetsValue"))
                .build()
                .limit();
        flatWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(5, flatWithOctetsRO.fixed1());
        assertEquals("value1", flatWithOctetsRO.string1().asString());
        final String octets2 = flatWithOctetsRO.octets2().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("12345", octets2);
        final String octets3 = flatWithOctetsRO.octets3().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("678", octets3);
        final String extension = flatWithOctetsRO.extension().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("octetsValue", extension);
    }

    @Test
    public void shouldSetOctetsValuesUsingBuffer() throws Exception
    {
        int limit = flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(asBuffer("1234567890"), 0, 10)
                .string1("value1")
                .octets2(asBuffer("12345"), 0, 5)
                .extension(asBuffer("octetsValue"), 0, "octetsValue".length())
                .build()
                .limit();
        flatWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(5, flatWithOctetsRO.fixed1());
        assertEquals("value1", flatWithOctetsRO.string1().asString());
        final String octetsValue = flatWithOctetsRO.extension().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("octetsValue", octetsValue);
    }

    @Test
    public void shouldSetStringValuesUsingString8FW() throws Exception
    {
        int limit = flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
                .string1(asString8FW("value1"))
                .octets2(b -> b.put("12345".getBytes(UTF_8)))
                .extension(b -> b.put("octetsValue".getBytes(UTF_8)))
                .build()
                .limit();
        flatWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(5, flatWithOctetsRO.fixed1());
        assertEquals("value1", flatWithOctetsRO.string1().asString());
    }

    @Test
    public void shouldSetStringValuesUsingBuffer() throws Exception
    {
        int limit = flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(5)
                .octets1(b -> b.put("1234567890".getBytes(UTF_8)))
                .string1(asBuffer("value1"), 0, "value1".length())
                .octets2(b -> b.put("12345".getBytes(UTF_8)))
                .extension(b -> b.put("octetsValue".getBytes(UTF_8)))
                .build()
                .limit();
        flatWithOctetsRO.wrap(buffer,  0,  limit);
        assertEquals(5, flatWithOctetsRO.fixed1());
        assertEquals("value1", flatWithOctetsRO.string1().asString());
    }

    private static DirectBuffer asBuffer(String value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(value.length()));
        valueBuffer.putStringWithoutLengthUtf8(0, value);
        return valueBuffer;
    }

    private static OctetsFW asOctetsFW(String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new OctetsFW.Builder().wrap(buffer, 0, buffer.capacity()).set(value.getBytes(UTF_8)).build();
    }

    private static String8FW asString8FW(String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
