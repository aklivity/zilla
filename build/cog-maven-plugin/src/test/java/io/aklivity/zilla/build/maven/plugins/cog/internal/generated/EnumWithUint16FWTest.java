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
import static org.agrona.BitUtil.SIZE_OF_SHORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint16;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint16FW;

public class EnumWithUint16FWTest
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

    private final EnumWithUint16FW.Builder flyweightRW = new EnumWithUint16FW.Builder();
    private final EnumWithUint16FW flyweightRO = new EnumWithUint16FW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putShort(offset, (short) EnumWithUint16.NI.value());
        return SIZE_OF_SHORT;
    }

    void assertAllTestValuesRead(
        EnumWithUint16FW flyweight)
    {
        assertEquals(EnumWithUint16.NI, flyweight.get());
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
        final int offset = 10;
        int length = setAllTestValues(buffer, offset);

        final EnumWithUint16FW enumWithUint16 = flyweightRO.tryWrap(buffer, offset, offset + length);

        assertNotNull(enumWithUint16);
        assertAllTestValuesRead(enumWithUint16);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        final int offset = 10;
        int length = setAllTestValues(buffer, offset);

        final EnumWithUint16FW enumWithUint16 = flyweightRO.wrap(buffer, offset, offset + length);

        assertEquals(offset + length, enumWithUint16.limit());
        assertAllTestValuesRead(enumWithUint16);
    }

    @Test
    public void shouldNotTryWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putShort(offset, (short) -2);

        final EnumWithUint16FW enumWithUint16 = flyweightRO.tryWrap(buffer, offset, buffer.capacity());

        assertNotNull(enumWithUint16);
        assertNull(enumWithUint16.get());
    }

    @Test
    public void shouldNotWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putShort(offset, (short) -2);

        final EnumWithUint16FW enumWithUint16 = flyweightRO.wrap(buffer, offset, buffer.capacity());

        assertNull(enumWithUint16.get());
    }

    @Test
    public void shouldSetUsingEnum()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(EnumWithUint16.NI)
            .build()
            .limit();
        setAllTestValues(expected,  0);
        assertEquals(SIZE_OF_SHORT, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetUsingEnumWithUint16FW()
    {
        EnumWithUint16FW enumWithInt16 = new EnumWithUint16FW().wrap(asBuffer(60001), 0, SIZE_OF_SHORT);
        int limit = flyweightRW.wrap(buffer, 10, 10 + SIZE_OF_SHORT)
            .set(enumWithInt16)
            .build()
            .limit();

        final EnumWithUint16FW enumWithUint16 = flyweightRO.wrap(buffer, 10,  limit);

        assertEquals(EnumWithUint16.ICHI, enumWithUint16.get());
        assertEquals(SIZE_OF_SHORT, enumWithUint16.sizeof());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10)
            .set(EnumWithUint16.ICHI);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingEnumWithUint16FWWithInsufficientSpace()
    {
        EnumWithUint16FW enumWithInt16 = new EnumWithUint16FW().wrap(asBuffer(60001), 0, SIZE_OF_SHORT);
        flyweightRW.wrap(buffer, 10, 10)
            .set(enumWithInt16);
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
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(SIZE_OF_SHORT));
        valueBuffer.putShort(0, (short) (value & 0xFFFF));
        return valueBuffer;
    }
}
