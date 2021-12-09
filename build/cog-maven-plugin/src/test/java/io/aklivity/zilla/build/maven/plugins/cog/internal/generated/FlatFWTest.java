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

import static io.aklivity.zilla.build.maven.plugins.cog.internal.generated.FlyweightTest.putMediumInt;
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
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.VarStringFW;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.FlatFW;

public class FlatFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final MutableDirectBuffer expected = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final FlatFW.Builder flatRW = new FlatFW.Builder();
    private final FlatFW flatRO = new FlatFW();
    private final String8FW.Builder stringRW = new String8FW.Builder();
    private final VarStringFW.Builder varstringRW = new VarStringFW.Builder();
    private final MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(100));

    @Test
    public void shouldProvideTypeId() throws Exception
    {
        int limit = flatRW.wrap(buffer, 0, 100)
                .fixed1(10)
                .string1("value1")
                .string2("value2")
                .string3("value3")
                .string4("value4")
                .build()
                .limit();
        flatRO.wrap(buffer,  0,  limit);
        assertEquals(0x10000001, FlatFW.TYPE_ID);
        assertEquals(0x10000001, flatRO.typeId());
    }

    @Test
    public void shouldNotTryWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int offsetString1 = Long.BYTES + Short.BYTES;
        buffer.putByte(10 + offsetString1, (byte) 0);
        int offsetString2 = offsetString1 + Byte.BYTES + Integer.BYTES;
        buffer.putByte(10 + offsetString2, (byte) 1);
        for (int maxLimit = 10; maxLimit < 10 + offsetString2 + Byte.BYTES; maxLimit++)
        {
            assertNull(flatRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int offsetString1 = Long.BYTES + Short.BYTES;
        buffer.putByte(10 + offsetString1, (byte) 0);
        int offsetString2 = offsetString1 + Byte.BYTES + Integer.BYTES;
        buffer.putByte(10 + offsetString2, (byte) 1);
        for (int maxLimit = 10; maxLimit < 10 + offsetString2 + Byte.BYTES; maxLimit++)
        {
            try
            {
                flatRO.wrap(buffer,  10, maxLimit);
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
        int offsetString1 = Long.BYTES + Short.BYTES;
        buffer.putByte(10 + offsetString1, (byte) 0);
        int offsetString2 = offsetString1 + Byte.BYTES + 3;
        buffer.putByte(10 + offsetString2, (byte) 0);
        int offsetString3 = offsetString2 + Byte.BYTES + Integer.BYTES;
        buffer.putByte(10 + offsetString3, (byte) 0);
        int offsetString4 = offsetString3 + Byte.BYTES + Byte.BYTES;
        buffer.putByte(10 + offsetString4, (byte) 0);
        assertSame(flatRO, flatRO.tryWrap(buffer, 10, 10 + offsetString4 + Byte.BYTES));
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int offsetString1 = Long.BYTES + Short.BYTES;
        buffer.putByte(10 + offsetString1, (byte) 0);
        int offsetString2 = offsetString1 + Byte.BYTES + 3;
        buffer.putByte(10 + offsetString2, (byte) 0);
        int offsetString3 = offsetString2 + Byte.BYTES + Integer.BYTES;
        buffer.putByte(10 + offsetString3, (byte) 0);
        int offsetString4 = offsetString3 + Byte.BYTES + Byte.BYTES;
        buffer.putByte(10 + offsetString4, (byte) 0);
        assertSame(flatRO, flatRO.wrap(buffer, 10, 10 + offsetString4 + Byte.BYTES));
    }

    @Test
    public void shouldRewrapAfterBuild()
    {
        flatRW.wrap(buffer, 0, 100)
                .fixed1(10)
                .string1("value1a")
                .string2("value2a")
                .string3("value3a")
                .string4("value4a")
                .build();

        final FlatFW flat = flatRW.rewrap()
                .fixed1(20)
                .string1("value1b")
                .string2("value2b")
                .string3("value3b")
                .string4("value4b")
                .build();

        assertSame(20L, flat.fixed1());
        assertEquals("value1b", flat.string1().asString());
        assertEquals("value2b", flat.string2().asString());
        assertEquals("value3b", flat.string3().asString());
        assertEquals("value4b", flat.string4().asString());
    }

    @Test
    public void shouldDefaultValues() throws Exception
    {
        int limit = flatRW.wrap(buffer, 0, 100)
                .fixed1(10)
                .string1("value1")
                .string2("value2")
                .string3("value3")
                .string4("value4")
                .build()
                .limit();
        flatRO.wrap(buffer,  0,  limit);
        assertEquals(222, flatRO.fixed2());
        assertEquals(333, flatRO.fixed3());
        assertEquals(444, flatRO.fixed4());
        assertEquals(55, flatRO.fixed5());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetFixed1WithInsufficientSpace()
    {
        flatRW.wrap(buffer, 10, 10)
               .fixed1(10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetFixed2WithInsufficientSpace()
    {
        flatRW.wrap(buffer, 10, 12)
                .fixed1(10)
                .fixed2(20);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WhenDefaultingFixed2ExceedsMaxLimit()
    {
        flatRW.wrap(buffer, 10, 12)
                .fixed1(10)
                .string1("");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetString1WhenExceedsMaxLimit()
    {
        flatRW.wrap(buffer, 10, 14)
                .fixed1(0x01)
                .fixed2(0x0101)
                .string1("1234");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetFixed3WithInsufficientSpace()
    {
        flatRW.wrap(buffer, 10, 15)
                .fixed1(10)
                .fixed2(20)
                .string1("")
                .fixed3(30);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetFixed2BeforeFixed1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
                .fixed2(10);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetString1BeforeFixed1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
                .string1("value1");
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetString2BeforeFixed1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
                .string2("value1");
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToSetString2BeforeString1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
                .fixed1(10)
                .string2("value1");
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetFixed1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .fixed1(101)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetString1() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .fixed2(111)
            .string1("value1")
            .string1("another value")
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenFixed1NotSet() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenString1NotSet() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToBuildWhenString2NotSet() throws Exception
    {
        flatRW.wrap(buffer, 0, 100)
            .fixed1(10)
            .fixed2(111)
            .string1("value1")
            .fixed3(33)
            .build();
    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        flatRW.wrap(buffer, 0, buffer.capacity())
                .fixed1(10)
                .fixed2(20)
                .string1("value1")
                .fixed3(30)
                .string2("value2")
                .fixed4(40)
                .string3("value3")
                .fixed5((byte) 50)
                .string4("value4")
                .build();
        flatRO.wrap(buffer,  0,  100);
        assertEquals(10, flatRO.fixed1());
        assertEquals(20, flatRO.fixed2());
        assertEquals("value1", flatRO.string1().asString());
        assertEquals(30, flatRO.fixed3());
        assertEquals("value2", flatRO.string2().asString());
        assertEquals(40, flatRO.fixed4());
        assertEquals("value3", flatRO.string3().asString());
        assertEquals(50, flatRO.fixed5());
        assertEquals("value4", flatRO.string4().asString());
    }

    @Test
    public void shouldSetStringValuesUsingFlyweight() throws Exception
    {
        FlatFW.Builder builder = flatRW.wrap(buffer, 0, buffer.capacity());
        builder.fixed1(10)
               .fixed2(20);
        String8FW value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
               .set("value1", UTF_8)
               .build();
        builder.string1(value)
               .fixed3(30);
        value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
               .set("value2", UTF_8)
               .build();
        builder.string2(value)
               .fixed4(40);
        value = stringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
                .set("value3", UTF_8)
                .build();
        builder.string3(value)
               .fixed5((byte) 50);
        VarStringFW varvalue = varstringRW.wrap(valueBuffer,  0, valueBuffer.capacity())
                .set("value4", UTF_8)
                .build();
        builder.string4(varvalue)
               .build();
        flatRO.wrap(buffer,  0,  100);
        assertEquals(10, flatRO.fixed1());
        assertEquals(20, flatRO.fixed2());
        assertEquals("value1", flatRO.string1().asString());
        assertEquals(30, flatRO.fixed3());
        assertEquals("value2", flatRO.string2().asString());
        assertEquals(40, flatRO.fixed4());
        assertEquals("value3", flatRO.string3().asString());
        assertEquals(50, flatRO.fixed5());
        assertEquals("value4", flatRO.string4().asString());
    }

    @Test
    public void shouldSetStringValuesUsingBuffer() throws Exception
    {
        valueBuffer.putStringWithoutLengthUtf8(0, "value1");
        valueBuffer.putStringWithoutLengthUtf8(10, "value2");
        valueBuffer.putStringWithoutLengthUtf8(20, "value3");
        valueBuffer.putStringWithoutLengthUtf8(30, "value4");
        flatRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(10)
            .fixed2(20)
            .string1(valueBuffer, 0, 6)
            .fixed3(30)
            .string2(valueBuffer, 10, 6)
            .fixed4(40)
            .string3(valueBuffer, 20, 6)
            .fixed5((byte) 50)
            .string4(valueBuffer, 30, 6)
            .build();
        flatRO.wrap(buffer,  0,  100);
        assertEquals(10, flatRO.fixed1());
        assertEquals(20, flatRO.fixed2());
        assertEquals("value1", flatRO.string1().asString());
        assertEquals(30, flatRO.fixed3());
        assertEquals("value2", flatRO.string2().asString());
        assertEquals(40, flatRO.fixed4());
        assertEquals("value3", flatRO.string3().asString());
        assertEquals(50, flatRO.fixed5());
        assertEquals("value4", flatRO.string4().asString());
    }

    @Test
    public void shouldSetStringValuesToNull() throws Exception
    {
        final int offset = 0;
        int limit = flatRW.wrap(buffer, offset, buffer.capacity())
            .fixed1(10)
            .string1((String) null)
            .string2((String) null)
            .string3((String) null)
            .string4((String) null)
            .build()
            .limit();
        expected.putLong(offset, 10);
        expected.putShort(offset + 8, (short) 222);
        expected.putByte(offset + 10, (byte) -1);
        putMediumInt(expected, offset + 11, 333);
        expected.putByte(offset + 14, (byte) -1);
        expected.putInt(offset + 15, 444);
        expected.putByte(offset + 19, (byte) -1);
        expected.putByte(offset + 20, (byte) 55);
        expected.putByte(offset + 21, (byte) 0);

        assertEquals(expected.byteBuffer(), buffer.byteBuffer());

        flatRO.wrap(buffer, offset, limit);
        assertNull(flatRO.string1().asString());
        assertNull(flatRO.string2().asString());
        assertNull(flatRO.string3().asString());
        assertNull(flatRO.string4().asString());
    }

    @Test
    public void shouldSetStringValuesToEmptyString() throws Exception
    {
        int limit = flatRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(10)
            .fixed2(20)
            .string1("")
            .fixed3(30)
            .string2("")
            .fixed4(40)
            .string3("")
            .fixed5((byte) 50)
            .string4("")
            .build()
            .limit();
        flatRO.wrap(buffer,  0,  limit);
        assertEquals("", flatRO.string1().asString());
        assertEquals("", flatRO.string2().asString());
        assertEquals("", flatRO.string3().asString());
        assertEquals("", flatRO.string4().asString());
    }

}
