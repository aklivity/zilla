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
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt64;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithInt64FW;

public class EnumWithInt64FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final EnumWithInt64FW.Builder flyweightRW = new EnumWithInt64FW.Builder();
    private final EnumWithInt64FW flyweightRO = new EnumWithInt64FW();

    static int setAllTestValues(MutableDirectBuffer buffer, final int offset)
    {
        int pos = offset;
        buffer.putLong(pos, EnumWithInt64.TWELVE.value());
        return SIZE_OF_LONG;
    }

    void assertAllTestValuesRead(EnumWithInt64FW flyweight)
    {
        assertEquals(EnumWithInt64.TWELVE, flyweight.get());
    }

    @Test
    public void shouldSetUsingEnum()
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(8))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
        MutableDirectBuffer expected = new UnsafeBuffer(allocateDirect(8))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
                               .set(EnumWithInt64.TWELVE)
                               .build()
                               .limit();
        setAllTestValues(expected,  0);
        assertEquals(8, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(10 + SIZE_OF_LONG))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull("at maxLimit " + maxLimit, flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenIncomplete()
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(10 + SIZE_OF_LONG))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
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
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(offset + SIZE_OF_LONG))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
        setAllTestValues(buffer, offset);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        final int offset = 10;
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(offset + SIZE_OF_LONG))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
        int size = setAllTestValues(buffer, offset);
        int limit = flyweightRO.wrap(buffer,  offset,  buffer.capacity()).limit();
        assertEquals(offset + size, limit);
        assertAllTestValuesRead(flyweightRO);
    }

    @Test
    public void shouldNotTryWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(offset + SIZE_OF_LONG))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
        buffer.putLong(offset, -2L);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertNull(flyweightRO.get());
    }

    @Test
    public void shouldNotWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(offset + SIZE_OF_LONG))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
        buffer.putLong(offset, -2L);
        flyweightRO.wrap(buffer, offset, buffer.capacity()).limit();
        assertNull(flyweightRO.get());
    }

    @Test
    public void shouldSetUsingEnumWithInt64FW()
    {
        int offset = 10;
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(offset + SIZE_OF_LONG))
        {
            {
                // Make sure the code is not secretly relying upon memory being initialized to 0
                setMemory(0, capacity(), (byte) 0xab);
            }
        };
        EnumWithInt64FW bigNumber = new EnumWithInt64FW().wrap(asBuffer(0x10L), 0, SIZE_OF_LONG);

        int limit = flyweightRW.wrap(buffer, offset, offset + SIZE_OF_LONG)
                               .set(bigNumber)
                               .build()
                               .limit();
        flyweightRO.wrap(buffer, offset, limit);
        assertEquals(EnumWithInt64.TEN, flyweightRO.get());
        assertEquals(SIZE_OF_LONG, flyweightRO.sizeof());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 16).set(EnumWithInt64.TEN);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingEnumWithInt64FWWithInsufficientSpace()
    {
        EnumWithInt64FW bigNumber = new EnumWithInt64FW().wrap(asBuffer(0x10L), 0, SIZE_OF_LONG);
        flyweightRW.wrap(buffer, 10, 16)
                   .set(bigNumber);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToBuildWithNothingSet()
    {
        flyweightRW.wrap(buffer, 10, buffer.capacity())
                   .build();
    }

    private static DirectBuffer asBuffer(long value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(8));
        valueBuffer.putLong(0, value);
        return valueBuffer;
    }
}
