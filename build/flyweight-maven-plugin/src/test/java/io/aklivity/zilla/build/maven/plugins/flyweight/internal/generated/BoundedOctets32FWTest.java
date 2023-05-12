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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.BoundedOctets32FW;

public class BoundedOctets32FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final BoundedOctets32FW.Builder flyweightRW = new BoundedOctets32FW.Builder();

    private final BoundedOctets32FW flyweightRO = new BoundedOctets32FW();

    private final int lengthSize = Integer.BYTES;


    private int setValue(
        MutableDirectBuffer buffer,
        int offset)
    {
        int length = 6;
        String value = "value1";

        buffer.putInt(offset, length);
        int offsetValue = offset + lengthSize;
        buffer.putBytes(offsetValue, value.getBytes(UTF_8));

        return lengthSize + value.length();
    }

    private void assertAllTestValuesRead(
        BoundedOctets32FW flyweight,
        int offset)
    {
        assertEquals(6, flyweight.length());
        assertEquals("value1", flyweight.get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o)));
        assertEquals(offset + lengthSize + flyweight.length(), flyweight.limit());
    }

    @Test
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        int length = setValue(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            try
            {
                flyweightRO.wrap(buffer, 10, maxLimit);
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
        int length = setValue(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setValue(buffer, 10);

        final BoundedOctets32FW boundedOctets32 = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, boundedOctets32);
        assertAllTestValuesRead(boundedOctets32, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setValue(buffer, 10);

        final BoundedOctets32FW boundedOctets32 =
            flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(boundedOctets32);
        assertSame(flyweightRO, boundedOctets32);
        assertAllTestValuesRead(boundedOctets32, 10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithByteArrayWhenExceedsMaxLimit()
    {
        flyweightRW.wrap(buffer, 0, 6)
            .set("value1".getBytes(UTF_8))
            .build();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithBoundedOctetsFWWhenExceedsMaxLimit()
    {
        flyweightRW.wrap(buffer, 0, 6)
            .set(asBoundedOctets32FW("value1"))
            .build();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithDirectBufferWhenExceedsMaxLimit()
    {
        flyweightRW.wrap(buffer, 0, 6)
            .set(asBuffer("value1"), 0, 6)
            .build();
    }

    @Test
    public void shouldSetWithBoundedOctetsFW() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBoundedOctets32FW("value1"))
            .build()
            .limit();

        final BoundedOctets32FW boundedOctets32 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(boundedOctets32, 0);
    }

    @Test
    public void shouldSetWithDirectBuffer() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBuffer("value1"), 0, 6)
            .build()
            .limit();

        final BoundedOctets32FW boundedOctets32 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(boundedOctets32, 0);
    }

    @Test
    public void shouldSetWithByteArray() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set("value1".getBytes(UTF_8))
            .build()
            .limit();

        final BoundedOctets32FW boundedOctets32 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(boundedOctets32, 0);
    }

    private static DirectBuffer asBuffer(
        String value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(value.length()));
        valueBuffer.putStringWithoutLengthUtf8(0, value);
        return valueBuffer;
    }

    private static BoundedOctets32FW asBoundedOctets32FW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Integer.BYTES + value.length()));
        return new BoundedOctets32FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value.getBytes(UTF_8)).build();
    }
}
