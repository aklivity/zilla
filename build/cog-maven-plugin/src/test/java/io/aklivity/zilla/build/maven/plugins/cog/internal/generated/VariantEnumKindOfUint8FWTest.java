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
import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.EnumWithUint8;
import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.VariantEnumKindOfUint8FW;

public class VariantEnumKindOfUint8FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantEnumKindOfUint8FW.Builder flyweightRW = new VariantEnumKindOfUint8FW.Builder();
    private final VariantEnumKindOfUint8FW flyweightRO = new VariantEnumKindOfUint8FW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putByte(offset, (byte) EnumWithUint8.ICHI.value());
        buffer.putByte(offset + SIZE_OF_BYTE, (byte) 200);
        return SIZE_OF_BYTE + SIZE_OF_BYTE;
    }

    void assertAllTestValuesRead(
        VariantEnumKindOfUint8FW flyweight)
    {
        assertEquals(200, flyweight.getAsUint8());
        assertEquals(200, flyweight.get());
        assertEquals(EnumWithUint8.ICHI, flyweight.kind());
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

        final VariantEnumKindOfUint8FW variantEnumKindOfUint8 = flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(variantEnumKindOfUint8);
        assertSame(flyweightRO, variantEnumKindOfUint8);
        assertAllTestValuesRead(variantEnumKindOfUint8);
    }

    @Test
    public void shouldWrapWhenLengthSufficientCase()
    {
        int length = setAllTestValues(buffer, 10);

        final VariantEnumKindOfUint8FW variantEnumKindOfUint8 = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, variantEnumKindOfUint8);
        assertAllTestValuesRead(variantEnumKindOfUint8);
    }

    @Test
    public void shouldTryWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        int length = setAllTestValues(buffer, offset);

        final VariantEnumKindOfUint8FW variantEnumKindOfUint8 = flyweightRO.tryWrap(buffer, offset, offset + length);

        assertNotNull(variantEnumKindOfUint8);
        assertAllTestValuesRead(variantEnumKindOfUint8);
    }

    @Test
    public void shouldWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        int length = setAllTestValues(buffer, offset);

        final VariantEnumKindOfUint8FW variantEnumKindOfUint8 = flyweightRO.wrap(buffer, offset, offset + length);

        assertAllTestValuesRead(variantEnumKindOfUint8);
    }

    @Test
    public void shouldSetAsUint8()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint8(200)
            .build()
            .limit();

        final VariantEnumKindOfUint8FW variantEnumKindOfUint8 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(variantEnumKindOfUint8);
    }

    @Test
    public void shouldSetInt8UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(200)
            .build()
            .limit();

        final VariantEnumKindOfUint8FW variantEnumKindOfUint8 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(variantEnumKindOfUint8);
    }

    @Test
    public void shouldSetZeroUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0)
            .build()
            .limit();

        final VariantEnumKindOfUint8FW variantEnumKindOfUint8 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(0, variantEnumKindOfUint8.getAsZero());
        assertEquals(0, variantEnumKindOfUint8.get());
        assertEquals(EnumWithUint8.NI, variantEnumKindOfUint8.kind());
    }

    @Test
    public void shouldSetOneUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(1)
            .build()
            .limit();

        final VariantEnumKindOfUint8FW variantEnumKindOfUint8 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(1, variantEnumKindOfUint8.getAsOne());
        assertEquals(1, variantEnumKindOfUint8.get());
        assertEquals(EnumWithUint8.SAN, variantEnumKindOfUint8.kind());
    }
}
