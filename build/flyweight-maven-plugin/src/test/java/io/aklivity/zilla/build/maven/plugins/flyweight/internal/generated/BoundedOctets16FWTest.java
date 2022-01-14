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

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.BoundedOctets16FW;

public class BoundedOctets16FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final BoundedOctets16FW.Builder flyweightRW = new BoundedOctets16FW.Builder();

    private final BoundedOctets16FW flyweightRO = new BoundedOctets16FW();

    private final int lengthSize = Short.BYTES;


    private int setValue(
        MutableDirectBuffer buffer,
        int offset)
    {
        int length = 6;
        String value = "value1";

        buffer.putShort(offset, (short) length);
        int offsetValue = offset + lengthSize;
        buffer.putBytes(offsetValue, value.getBytes(UTF_8));

        return lengthSize + value.length();
    }

    private void assertAllTestValuesRead(
        BoundedOctets16FW flyweight,
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

        final BoundedOctets16FW boundedOctets16 = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, boundedOctets16);
        assertAllTestValuesRead(boundedOctets16, 10);
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = setValue(buffer, 10);

        final BoundedOctets16FW boundedOctets16 =
            flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(boundedOctets16);
        assertSame(flyweightRO, boundedOctets16);
        assertAllTestValuesRead(boundedOctets16, 10);
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
            .set(asBoundedOctets16FW("value1"))
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
            .set(asBoundedOctets16FW("value1"))
            .build()
            .limit();

        final BoundedOctets16FW boundedOctets16 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(boundedOctets16, 0);
    }

    @Test
    public void shouldSetWithDirectBuffer() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(asBuffer("value1"), 0, 6)
            .build()
            .limit();

        final BoundedOctets16FW boundedOctets16 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(boundedOctets16, 0);
    }

    @Test
    public void shouldSetWithByteArray() throws Exception
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set("value1".getBytes(UTF_8))
            .build()
            .limit();

        final BoundedOctets16FW boundedOctets16 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(boundedOctets16, 0);
    }

    private static DirectBuffer asBuffer(
        String value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(value.length()));
        valueBuffer.putStringWithoutLengthUtf8(0, value);
        return valueBuffer;
    }

    private static BoundedOctets16FW asBoundedOctets16FW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Short.BYTES + value.length()));
        return new BoundedOctets16FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value.getBytes(UTF_8)).build();
    }
}
