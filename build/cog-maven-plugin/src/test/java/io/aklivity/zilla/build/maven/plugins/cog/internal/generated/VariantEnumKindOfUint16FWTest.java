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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint16;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantEnumKindOfUint16FW;

public class VariantEnumKindOfUint16FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantEnumKindOfUint16FW.Builder flyweightRW = new VariantEnumKindOfUint16FW.Builder();
    private final VariantEnumKindOfUint16FW flyweightRO = new VariantEnumKindOfUint16FW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putShort(offset, (short) EnumWithUint16.ICHI.value());
        buffer.putShort(offset + SIZE_OF_SHORT, (short) 60000);
        return SIZE_OF_SHORT + SIZE_OF_SHORT;
    }

    void assertAllTestValuesRead(
        VariantEnumKindOfUint16FW flyweight)
    {
        assertEquals(60000, flyweight.getAsUint16());
        assertEquals(60000, flyweight.get());
        assertEquals(EnumWithUint16.ICHI, flyweight.kind());
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int length = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + length; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
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
    public void shouldTryWrapWhenLengthSufficientCase()
    {
        int length = setAllTestValues(buffer, 10);

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(variantEnumKindOfUint16);
        assertSame(flyweightRO, variantEnumKindOfUint16);
    }

    @Test
    public void shouldWrapWhenLengthSufficientCase()
    {
        int length = setAllTestValues(buffer, 10);

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, variantEnumKindOfUint16);
    }

    @Test
    public void shouldTryWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        int length = setAllTestValues(buffer, offset);

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.tryWrap(buffer, offset, offset + length);

        assertNotNull(variantEnumKindOfUint16);
        assertAllTestValuesRead(variantEnumKindOfUint16);
    }

    @Test
    public void shouldWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        int length = setAllTestValues(buffer, offset);

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.wrap(buffer, offset, offset + length);

        assertAllTestValuesRead(variantEnumKindOfUint16);
    }

    @Test
    public void shouldSetAsUint8()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint8(200)
            .build()
            .limit();

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(200, variantEnumKindOfUint16.getAsUint8());
        assertEquals(200, variantEnumKindOfUint16.get());
        assertEquals(EnumWithUint16.NI, variantEnumKindOfUint16.kind());
    }

    @Test
    public void shouldSetAsUint16()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint16(60000)
            .build()
            .limit();

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(variantEnumKindOfUint16);
    }

    @Test
    public void shouldSetAsZero()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsZero()
            .build()
            .limit();

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(0, variantEnumKindOfUint16.getAsZero());
        assertEquals(0, variantEnumKindOfUint16.get());
        assertEquals(EnumWithUint16.SAN, variantEnumKindOfUint16.kind());
    }

    @Test
    public void shouldSetUint8UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(200)
            .build()
            .limit();

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(200, variantEnumKindOfUint16.getAsUint8());
        assertEquals(200, variantEnumKindOfUint16.get());
        assertEquals(EnumWithUint16.NI, variantEnumKindOfUint16.kind());
    }

    @Test
    public void shouldSetUint16UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(60000)
            .build()
            .limit();

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(variantEnumKindOfUint16);
    }

    @Test
    public void shouldSetZeroUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0)
            .build()
            .limit();

        final VariantEnumKindOfUint16FW variantEnumKindOfUint16 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(0, variantEnumKindOfUint16.getAsZero());
        assertEquals(0, variantEnumKindOfUint16.get());
        assertEquals(EnumWithUint16.SAN, variantEnumKindOfUint16.kind());
    }
}
