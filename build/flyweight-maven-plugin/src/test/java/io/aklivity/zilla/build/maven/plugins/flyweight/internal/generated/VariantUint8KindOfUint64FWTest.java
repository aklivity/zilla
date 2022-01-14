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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantUint8KindOfUint64FW;

public class VariantUint8KindOfUint64FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantUint8KindOfUint64FW.Builder flyweightRW = new VariantUint8KindOfUint64FW.Builder();
    private final VariantUint8KindOfUint64FW flyweightRO = new VariantUint8KindOfUint64FW();

    private static int setAllTestValuesCaseUint8(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putByte(offset, (byte) 0x53);
        buffer.putByte(offset + SIZE_OF_BYTE, (byte) 0xFF);
        return SIZE_OF_BYTE + SIZE_OF_BYTE;
    }

    private void assertAllTestValuesReadCaseUint8(
        VariantUint8KindOfUint64FW flyweight)
    {
        assertEquals(0xFF, flyweight.getAsUint8());
        assertEquals(0xFF, flyweight.get());
        assertEquals(0x53, flyweight.kind());
    }

    private void assertAllTestValuesReadCaseUint16(
        VariantUint8KindOfUint64FW flyweight)
    {
        assertEquals(0xFFFF, flyweight.getAsUint16());
        assertEquals(0xFFFF, flyweight.get());
        assertEquals(0x60, flyweight.kind());
    }

    private void assertAllTestValuesReadCaseUint24(
        VariantUint8KindOfUint64FW flyweight)
    {
        assertEquals(0xFFFF_FF, flyweight.getAsUint24());
        assertEquals(0xFFFF_FF, flyweight.get());
        assertEquals(0x50, flyweight.kind());
    }

    private void assertAllTestValuesReadCaseUint32(
        VariantUint8KindOfUint64FW flyweight)
    {
        assertEquals(0xFFFF_FFFFL, flyweight.getAsUint32());
        assertEquals(0xFFFF_FFFFL, flyweight.get());
        assertEquals(0x70, flyweight.kind());
    }

    private void assertAllTestValuesReadCaseUint64(
        VariantUint8KindOfUint64FW flyweight)
    {
        assertEquals(0xFFFF_FFFF_FL, flyweight.getAsUint64());
        assertEquals(0xFFFF_FFFF_FL, flyweight.get());
        assertEquals(0x80, flyweight.kind());
    }

    @Test
    public void shouldNotTryWrapWhenIncompleteCaseUint8()
    {
        int length = setAllTestValuesCaseUint8(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
        }
    }

    @Test
    public void shouldNotWrapWhenIncompleteCaseUint8()
    {
        int length = setAllTestValuesCaseUint8(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            try
            {
                flyweightRO.wrap(buffer,  10, maxLimit);
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
    public void shouldTryWrapWhenLengthSufficientCaseUint8()
    {
        int length = setAllTestValuesCaseUint8(buffer, 10);

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(variantUint8KindOfUint64);
        assertSame(flyweightRO, variantUint8KindOfUint64);
    }

    @Test
    public void shouldWrapWhenLengthSufficientCaseUint8()
    {
        int length = setAllTestValuesCaseUint8(buffer, 10);

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, variantUint8KindOfUint64);
    }

    @Test
    public void shouldTryWrapAndReadAllValuesCaseUint8() throws Exception
    {
        final int offset = 1;
        int length = setAllTestValuesCaseUint8(buffer, offset);

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.tryWrap(buffer, offset, offset + length);

        assertNotNull(variantUint8KindOfUint64);
        assertAllTestValuesReadCaseUint8(variantUint8KindOfUint64);
    }

    @Test
    public void shouldWrapAndReadAllValuesCaseUint8() throws Exception
    {
        final int offset = 1;
        int length = setAllTestValuesCaseUint8(buffer, offset);

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, offset, offset + length);

        assertAllTestValuesReadCaseUint8(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint64()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint64(0xFFFF_FFFF_FL)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint64(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint32()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint32(0xFFFF_FFFFL)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint32(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint24()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint24(0xFFFF_FF)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint24(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint16()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint16(0xFFFF)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint16(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint8()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint8(0xFF)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint8(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint64UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0xFFFF_FFFF_FL)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint64(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint32UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0xFFFF_FFFFL)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint32(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint16UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0xFFFF)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint16(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetUint8UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0xFF)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesReadCaseUint8(variantUint8KindOfUint64);
    }

    @Test
    public void shouldSetZeroUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(0, variantUint8KindOfUint64.getAsZero());
        assertEquals(0, variantUint8KindOfUint64.get());
        assertEquals(0x44, variantUint8KindOfUint64.kind());
    }

    @Test
    public void shouldSetOneUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(1)
            .build()
            .limit();

        final VariantUint8KindOfUint64FW variantUint8KindOfUint64 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(1, variantUint8KindOfUint64.getAsOne());
        assertEquals(1, variantUint8KindOfUint64.get());
        assertEquals(0x01, variantUint8KindOfUint64.kind());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUint32WithInsufficientSpace()
    {
        flyweightRW.wrap(buffer, 10, 14)
            .setAsUint32(12345);
    }
}
