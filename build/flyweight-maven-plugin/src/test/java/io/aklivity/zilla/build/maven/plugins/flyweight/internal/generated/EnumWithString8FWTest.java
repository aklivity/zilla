/*
 * Copyright 2021-2024 Aklivity Inc.
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
import static org.junit.Assert.fail;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithString;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithStringFW;

public class EnumWithString8FWTest
{
    private static final int LENGTH_SIZE = BitUtil.SIZE_OF_BYTE;

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
    private final EnumWithStringFW.Builder flyweightRW = new EnumWithStringFW.Builder();
    private final EnumWithStringFW flyweightRO = new EnumWithStringFW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset,
        String value)
    {
        int pos = offset;
        byte[] charBytes = value.getBytes(UTF_8);
        buffer.putByte(pos, (byte) charBytes.length);
        buffer.putBytes(pos + 1, charBytes);
        return charBytes.length + LENGTH_SIZE;
    }

    void assertAllTestValuesRead(
        EnumWithStringFW flyweight)
    {
        assertEquals(EnumWithString.BLUE, flyweight.get());
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10, "blue");
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull("at maxLimit " + maxLimit, flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10, "blue");
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            try
            {
                flyweightRO.wrap(buffer,  10, maxLimit);
                fail("Exception not thrown for maxLimit " + maxLimit);
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
    public void shouldTryWrapAndReadAllValues() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset, "blue");
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        int size = setAllTestValues(buffer, 10, "blue");
        int limit = flyweightRO.wrap(buffer,  10,  buffer.capacity()).limit();
        assertEquals(10 + size, limit);
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldNotTryInvalidValue() throws Exception
    {
        final int offset = 0;
        byte[] charBytes = "blue".getBytes(UTF_8);
        buffer.putByte(offset,  (byte) 254);
        buffer.putBytes(offset + 1, charBytes);
        assertNull(flyweightRO.tryWrap(buffer, offset, offset + charBytes.length + LENGTH_SIZE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldWNotrapAndReadInvalidValue() throws Exception
    {
        final int offset = 0;
        byte[] charBytes = "blue".getBytes(UTF_8);
        buffer.putByte(offset,  (byte) 2);
        buffer.putBytes(offset + 1, charBytes);
        flyweightRO.wrap(buffer, offset, offset + charBytes.length + LENGTH_SIZE);
        assertNull(flyweightRO.get());
    }

    @Test
    public void shouldSetUsingEnum()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(EnumWithString.BLUE, UTF_8)
            .build()
            .limit();
        setAllTestValues(expected, 0, EnumWithString.BLUE.value());
        assertEquals(LENGTH_SIZE + EnumWithString.BLUE.value().getBytes(UTF_8).length, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetUsingEnumWithStringFW()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asEnumWithStringFW(EnumWithString.BLUE))
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(EnumWithString.BLUE, flyweightRO.get());
        assertEquals(4 + LENGTH_SIZE, flyweightRO.limit());
        assertEquals(4 + LENGTH_SIZE, flyweightRO.sizeof());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10)
            .set(EnumWithString.BLUE, UTF_8);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingEnumWithStringFWWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10)
            .set(asEnumWithStringFW(EnumWithString.BLUE));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToBuildWithNothingSet()
    {
        flyweightRW.wrap(buffer, 10, buffer.capacity()).build();
    }

    private static EnumWithStringFW asEnumWithStringFW(EnumWithString value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(LENGTH_SIZE + value.value().length()));
        return new EnumWithStringFW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }
}
