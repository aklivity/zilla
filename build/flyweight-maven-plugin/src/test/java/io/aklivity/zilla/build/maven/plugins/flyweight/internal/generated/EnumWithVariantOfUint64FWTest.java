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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithVariantOfUint64;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithVariantOfUint64FW;

public class EnumWithVariantOfUint64FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final EnumWithVariantOfUint64FW.Builder flyweightRW = new EnumWithVariantOfUint64FW.Builder();
    private final EnumWithVariantOfUint64FW flyweightRO = new EnumWithVariantOfUint64FW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putByte(offset, (byte) 0x01);
        buffer.putByte(offset + SIZE_OF_BYTE, (byte) 0x53);
        buffer.putByte(offset + SIZE_OF_BYTE + SIZE_OF_BYTE, (byte) 0x11);
        return 3 * SIZE_OF_BYTE;
    }

    void assertAllTestValuesRead(
        EnumWithVariantOfUint64FW flyweight)
    {
        assertEquals(EnumWithVariantOfUint64.TYPE2, flyweight.get());
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

        final EnumWithVariantOfUint64FW enumWithVariantOfUint64 = flyweightRO.tryWrap(buffer, offset, length + offset);

        assertNotNull(enumWithVariantOfUint64);
        assertAllTestValuesRead(enumWithVariantOfUint64);
    }

    @Test
    public void shouldWrapAndReadAllValues() throws Exception
    {
        final int offset = 10;
        int length = setAllTestValues(buffer, offset);

        final EnumWithVariantOfUint64FW enumWithVariantOfUint64 = flyweightRO.wrap(buffer,  10,  offset + length);

        assertEquals(offset + length, enumWithVariantOfUint64.limit());
        assertAllTestValuesRead(enumWithVariantOfUint64);
    }

    @Test
    public void shouldNotTryWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putByte(offset, (byte) 0x01);
        buffer.putByte(offset + SIZE_OF_BYTE, (byte) 0x53);
        buffer.putByte(offset + SIZE_OF_BYTE + SIZE_OF_BYTE, (byte) -2);

        final EnumWithVariantOfUint64FW enumWithVariantOfUint64 = flyweightRO.tryWrap(buffer, offset, buffer.capacity());

        assertNotNull(enumWithVariantOfUint64);
        assertNull(enumWithVariantOfUint64.get());
    }

    @Test
    public void shouldNotWrapAndReadInvalidValue() throws Exception
    {
        final int offset = 12;
        buffer.putByte(offset, (byte) 0x01);
        buffer.putByte(offset + SIZE_OF_BYTE, (byte) 0x53);
        buffer.putByte(offset + SIZE_OF_BYTE + SIZE_OF_BYTE, (byte) -2);
        final EnumWithVariantOfUint64FW enumWithVariantOfUint64 = flyweightRO.wrap(buffer, offset, buffer.capacity());
        assertNull(enumWithVariantOfUint64.get());
    }

    @Test
    public void shouldSetUsingEnum()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(EnumWithVariantOfUint64.TYPE2)
            .build()
            .limit();

        final EnumWithVariantOfUint64FW enumWithVariantOfUint64 = flyweightRO.wrap(buffer, 0,  limit);

        assertEquals(3, limit);
        assertEquals(EnumWithVariantOfUint64.TYPE2, enumWithVariantOfUint64.get());
    }

    @Test
    public void shouldSetUsingFlyweight()
    {
        EnumWithVariantOfUint64FW value = asEnumWithVariantOfUint64FW(EnumWithVariantOfUint64.TYPE2);
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(value)
            .build()
            .limit();

        final EnumWithVariantOfUint64FW enumWithVariantOfUint64 = flyweightRO.wrap(buffer, 0,  limit);

        assertEquals(3, limit);
        assertEquals(EnumWithVariantOfUint64.TYPE2, enumWithVariantOfUint64.get());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetWithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 10)
            .set(EnumWithVariantOfUint64.TYPE2);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingFlyweightWithInsufficientSpace()
    {
        EnumWithVariantOfUint64FW enumWithVariantOfUint64 = asEnumWithVariantOfUint64FW(EnumWithVariantOfUint64.TYPE2);
        flyweightRW.wrap(buffer, 10, 10)
            .set(enumWithVariantOfUint64);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailToBuildWithNothingSet()
    {
        flyweightRW.wrap(buffer, 10, buffer.capacity())
            .build();
    }

    private static EnumWithVariantOfUint64FW asEnumWithVariantOfUint64FW(
        EnumWithVariantOfUint64 value)
    {
        MutableDirectBuffer valueBuffer = new UnsafeBuffer(allocateDirect(3));
        return new EnumWithVariantOfUint64FW.Builder().wrap(valueBuffer, 0, valueBuffer.capacity()).set(value).build();
    }
}
