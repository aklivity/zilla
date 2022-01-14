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
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithUint32;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantEnumKindOfUint32FW;

public class VariantEnumKindOfUint32FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantEnumKindOfUint32FW.Builder flyweightRW = new VariantEnumKindOfUint32FW.Builder();
    private final VariantEnumKindOfUint32FW flyweightRO = new VariantEnumKindOfUint32FW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putInt(offset, (int) EnumWithUint32.NI.value());
        buffer.putInt(offset + SIZE_OF_INT, (int) 4000000000L);
        return SIZE_OF_INT + SIZE_OF_INT;
    }

    void assertAllTestValuesRead(
        VariantEnumKindOfUint32FW flyweight)
    {
        assertEquals(4000000000L, flyweight.getAsUint32());
        assertEquals(4000000000L, flyweight.get());
        assertEquals(EnumWithUint32.NI, flyweight.kind());
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

        final VariantEnumKindOfUint32FW variantEnumKindOfUint32 = flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(variantEnumKindOfUint32);
        assertSame(flyweightRO, variantEnumKindOfUint32);
        assertAllTestValuesRead(variantEnumKindOfUint32);
    }

    @Test
    public void shouldWrapWhenLengthSufficientCase()
    {
        int length = setAllTestValues(buffer, 10);

        final VariantEnumKindOfUint32FW variantEnumKindOfUint32 = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, variantEnumKindOfUint32);
        assertAllTestValuesRead(variantEnumKindOfUint32);
    }

    @Test
    public void shouldTryWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        int length = setAllTestValues(buffer, offset);

        final VariantEnumKindOfUint32FW variantEnumKindOfUint32 = flyweightRO.tryWrap(buffer, offset, offset + length);

        assertNotNull(variantEnumKindOfUint32);
        assertAllTestValuesRead(variantEnumKindOfUint32);
    }

    @Test
    public void shouldWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        int length = setAllTestValues(buffer, offset);

        final VariantEnumKindOfUint32FW variantEnumKindOfUint32 = flyweightRO.wrap(buffer, offset, offset + length);

        assertAllTestValuesRead(variantEnumKindOfUint32);
    }

    @Test
    public void shouldSetAsUint32()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint32(4000000000L)
            .build()
            .limit();

        final VariantEnumKindOfUint32FW variantEnumKindOfUint32 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(variantEnumKindOfUint32);
    }

    @Test
    public void shouldSetUint32UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(4000000000L)
            .build()
            .limit();

        final VariantEnumKindOfUint32FW variantEnumKindOfUint32 = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(variantEnumKindOfUint32);
    }

    @Test
    public void shouldSetZeroUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0)
            .build()
            .limit();

        final VariantEnumKindOfUint32FW variantEnumKindOfUint32 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(0, variantEnumKindOfUint32.getAsZero());
        assertEquals(0, variantEnumKindOfUint32.get());
        assertEquals(EnumWithUint32.ICHI, variantEnumKindOfUint32.kind());
    }

    @Test
    public void shouldSetOneUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(1)
            .build()
            .limit();

        final VariantEnumKindOfUint32FW variantEnumKindOfUint32 = flyweightRO.wrap(buffer, 0, limit);

        assertEquals(1, variantEnumKindOfUint32.getAsOne());
        assertEquals(1, variantEnumKindOfUint32.get());
        assertEquals(EnumWithUint32.SAN, variantEnumKindOfUint32.kind());
    }
}
