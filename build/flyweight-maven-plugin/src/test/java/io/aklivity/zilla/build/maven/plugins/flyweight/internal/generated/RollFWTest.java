/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.Roll;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.RollFW;

public class RollFWTest
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
    private final RollFW.Builder flyweightRW = new RollFW.Builder();
    private final RollFW flyweightRO = new RollFW();

    static int setAllTestValues(MutableDirectBuffer buffer, final int offset)
    {
        int pos = offset;
        buffer.putByte(pos,  (byte) Roll.SPRING.ordinal());
        return pos - offset + Byte.BYTES;
    }

    void assertAllTestValuesRead(RollFW flyweight)
    {
        assertEquals(Roll.SPRING, flyweight.get());
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
        buffer.putByte(offset,  (byte) -2);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertNull(flyweightRO.get());
    }

    @Test
    public void shouldWNotrapAndReadInvalidValue() throws Exception
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
               .set(Roll.SPRING)
               .build()
               .limit();
        setAllTestValues(expected,  0);
        assertEquals(1, limit);
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetUsingRollFW()
    {
        RollFW roll = new RollFW().wrap(asBuffer((byte) 0), 0, 1);
        int limit = flyweightRW.wrap(buffer, 10, 11)
               .set(roll)
               .build()
               .limit();
        flyweightRO.wrap(buffer, 10,  limit);
        assertEquals(Roll.EGG, flyweightRO.get());
        assertEquals(1, flyweightRO.sizeof());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10)
               .set(Roll.EGG);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingRollFWWithInsufficientSpace()
    {
        RollFW roll = new RollFW().wrap(asBuffer((byte) 0), 0, 1);
        flyweightRW.wrap(buffer, 10, 10)
              .set(roll);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToBuildWithNothingSet()
    {
        flyweightRW.wrap(buffer, 10, buffer.capacity())
            .build();
    }

    private static DirectBuffer asBuffer(byte value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(1));
        valueBuffer.putByte(0, value);
        return valueBuffer;
    }

}
