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
import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint8FW;

public class EnumWithUint8FWTest
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

    private final EnumWithUint8FW.Builder flyweightRW = new EnumWithUint8FW.Builder();
    private final EnumWithUint8FW flyweightRO = new EnumWithUint8FW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putByte(offset, (byte) EnumWithUint8.NI.value());
        return SIZE_OF_BYTE;
    }

    void assertAllTestValuesRead(
        EnumWithUint8FW flyweight)
    {
        assertEquals(EnumWithUint8.NI, flyweight.get());
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull("at maxLimit " + maxLimit, flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10);
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
        int size = setAllTestValues(buffer, 10);

        final EnumWithUint8FW enumWithUint8 = flyweightRO.tryWrap(buffer, 10, 10 + size);

        assertNotNull(enumWithUint8);
        assertAllTestValuesRead(enumWithUint8);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        int size = setAllTestValues(buffer, 10);

        final EnumWithUint8FW enumWithUint8 = flyweightRO.wrap(buffer, 10, 10 + size);

        assertEquals(10 + size, enumWithUint8.limit());
        assertAllTestValuesRead(enumWithUint8);
    }

    @Test
    public void shouldNotTryWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putByte(offset, (byte) -2);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertNull(flyweightRO.get());
    }

    @Test
    public void shouldNotWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putByte(offset,  (byte) -2);
        flyweightRO.wrap(buffer, offset, buffer.capacity()).limit();
        assertNull(flyweightRO.get());
    }

    @Test
    public void shouldSetUsingEnum()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(EnumWithUint8.NI)
            .build()
            .limit();
        setAllTestValues(expected,  0);
        assertEquals(SIZE_OF_BYTE, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetUsingEnumWithUint8FW()
    {
        EnumWithUint8FW enumWithInt8 = new EnumWithUint8FW().wrap(asBuffer(201), 0, SIZE_OF_BYTE);
        int limit = flyweightRW.wrap(buffer, 10, 10 + SIZE_OF_BYTE)
            .set(enumWithInt8)
            .build()
            .limit();
        final EnumWithUint8FW enumWithUint8 = flyweightRO.wrap(buffer, 10,  limit);
        assertEquals(EnumWithUint8.ICHI, enumWithUint8.get());
        assertEquals(SIZE_OF_BYTE, enumWithUint8.sizeof());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10)
            .set(EnumWithUint8.ICHI);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingEnumWithUint8FWWithInsufficientSpace()
    {
        EnumWithUint8FW enumWithInt8 = new EnumWithUint8FW().wrap(asBuffer(201), 0, SIZE_OF_BYTE);
        flyweightRW.wrap(buffer, 10, 10)
            .set(enumWithInt8);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToBuildWithNothingSet()
    {
        flyweightRW.wrap(buffer, 10, buffer.capacity())
            .build();
    }

    private static DirectBuffer asBuffer(
        int value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(SIZE_OF_BYTE));
        valueBuffer.putByte(0, (byte) value);
        return valueBuffer;
    }
}
