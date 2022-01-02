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

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt16;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt16FW;

public class EnumWithInt16FWTest
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

    private final EnumWithInt16FW.Builder flyweightRW = new EnumWithInt16FW.Builder();
    private final EnumWithInt16FW flyweightRO = new EnumWithInt16FW();

    static int setAllTestValues(MutableDirectBuffer buffer, final int offset)
    {
        int pos = offset;
        buffer.putShort(pos,  EnumWithInt16.TWO.value());
        return SIZE_OF_SHORT;
    }

    void assertAllTestValuesRead(EnumWithInt16FW flyweight)
    {
        assertEquals(EnumWithInt16.TWO, flyweight.get());
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
        final int offset = 1;
        setAllTestValues(buffer, offset);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        int size = setAllTestValues(buffer, 10);
        int limit = flyweightRO.wrap(buffer,  10,  buffer.capacity()).limit();
        assertEquals(10 + size, limit);
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldNotTryWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putShort(offset,  (short) -2);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertNull(flyweightRO.get());
    }

    @Test
    public void shouldNotWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putShort(offset,  (short) -2);
        flyweightRO.wrap(buffer, offset, buffer.capacity()).limit();
        assertNull(flyweightRO.get());
    }

    @Test
    public void shouldSetUsingEnum()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(EnumWithInt16.TWO)
            .build()
            .limit();
        setAllTestValues(expected,  0);
        assertEquals(SIZE_OF_SHORT, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetUsingEnumWithInt16FW()
    {
        EnumWithInt16FW enumWithInt16 = new EnumWithInt16FW().wrap(asBuffer((short) 1), 0, SIZE_OF_SHORT);
        int limit = flyweightRW.wrap(buffer, 10, 10 + SIZE_OF_SHORT)
            .set(enumWithInt16)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 10,  limit);
        assertEquals(EnumWithInt16.ONE, flyweightRO.get());
        assertEquals(SIZE_OF_SHORT, flyweightRO.sizeof());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10)
            .set(EnumWithInt16.ONE);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingEnumWithInt16FWWithInsufficientSpace()
    {
        EnumWithInt16FW enumWithInt8 = new EnumWithInt16FW().wrap(asBuffer((short) 1), 0, SIZE_OF_SHORT);
        flyweightRW.wrap(buffer, 10, 10)
            .set(enumWithInt8);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToBuildWithNothingSet()
    {
        flyweightRW.wrap(buffer, 10, buffer.capacity())
            .build();
    }

    private static DirectBuffer asBuffer(short value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(SIZE_OF_SHORT));
        valueBuffer.putShort(0, value);
        return valueBuffer;
    }
}
