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
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint64;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint64FW;

public class EnumWithUint64FWTest
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

    private final EnumWithUint64FW.Builder flyweightRW = new EnumWithUint64FW.Builder();
    private final EnumWithUint64FW flyweightRO = new EnumWithUint64FW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putLong(offset,  EnumWithUint64.NI.value());
        return SIZE_OF_LONG;
    }

    void assertAllTestValuesRead(
        EnumWithUint64FW flyweight)
    {
        assertEquals(EnumWithUint64.NI, flyweight.get());
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int length = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull("at maxLimit " + maxLimit, flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenIncomplete()
    {
        int length = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
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
        final int offset = 10;
        int length = setAllTestValues(buffer, offset);

        final EnumWithUint64FW enumWithUint64 = flyweightRO.tryWrap(buffer, offset, length + offset);

        assertNotNull(enumWithUint64);
        assertAllTestValuesRead(enumWithUint64);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        final int offset = 10;
        int length = setAllTestValues(buffer, offset);

        final EnumWithUint64FW enumWithUint64 = flyweightRO.wrap(buffer,  10,  offset + length);

        assertEquals(offset + length, enumWithUint64.limit());
        assertAllTestValuesRead(enumWithUint64);
    }

    @Test
    public void shouldNotTryWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putLong(offset,  -2);

        final EnumWithUint64FW enumWithUint64 = flyweightRO.tryWrap(buffer, offset, buffer.capacity());

        assertNotNull(enumWithUint64);
        assertNull(enumWithUint64.get());
    }

    @Test
    public void shouldNotWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putLong(offset,  -2);
        final EnumWithUint64FW enumWithUint64 = flyweightRO.wrap(buffer, offset, buffer.capacity());
        assertNull(enumWithUint64.get());
    }

    @Test
    public void shouldSetUsingEnum()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(EnumWithUint64.NI)
            .build()
            .limit();
        setAllTestValues(expected,  0);
        assertEquals(SIZE_OF_LONG, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetUsingEnumWithUint64FW()
    {
        EnumWithUint64FW enumWithUint64 = new EnumWithUint64FW().wrap(asBuffer(4000000001L), 0, SIZE_OF_LONG);
        int limit = flyweightRW.wrap(buffer, 10, 10 + SIZE_OF_LONG)
            .set(enumWithUint64)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 10,  limit);
        assertEquals(EnumWithUint64.ICHI, flyweightRO.get());
        assertEquals(SIZE_OF_LONG, flyweightRO.sizeof());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10)
            .set(EnumWithUint64.ICHI);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingEnumWithUint64FWWithInsufficientSpace()
    {
        EnumWithUint64FW enumWithUint64 = new EnumWithUint64FW().wrap(asBuffer(4000000001L), 0, SIZE_OF_LONG);
        flyweightRW.wrap(buffer, 10, 10)
            .set(enumWithUint64);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToBuildWithNothingSet()
    {
        flyweightRW.wrap(buffer, 10, buffer.capacity())
            .build();
    }

    private static DirectBuffer asBuffer(
        long value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(SIZE_OF_LONG));
        valueBuffer.putLong(0, value);
        return valueBuffer;
    }
}
