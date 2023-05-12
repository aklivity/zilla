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
import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantWithVariantCaseFW;

public class VariantWithVariantCaseFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantWithVariantCaseFW.Builder flyweightRW = new VariantWithVariantCaseFW.Builder();
    private final VariantWithVariantCaseFW flyweightRO = new VariantWithVariantCaseFW();

    private static int setAllTestValuesCaseUint8(
        MutableDirectBuffer buffer,
        final int offset)
    {
        buffer.putByte(offset, (byte) 0x01);
        buffer.putByte(offset + SIZE_OF_BYTE, (byte) 0x53);
        buffer.putByte(offset + SIZE_OF_BYTE + SIZE_OF_BYTE, (byte) 0x10);
        return 3 * SIZE_OF_BYTE;
    }

    private void assertAllTestValuesRead(
        VariantWithVariantCaseFW flyweight,
        int offset)
    {
        assertEquals(0x10, flyweight.get());
        assertEquals(1, flyweight.kind().value());
        assertEquals(offset + 3, flyweight.limit());
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

        final VariantWithVariantCaseFW variantWithVariantCase = flyweightRO.tryWrap(buffer, 10, 10 + length);

        assertNotNull(variantWithVariantCase);
        assertSame(flyweightRO, variantWithVariantCase);
    }

    @Test
    public void shouldWrapWhenLengthSufficientCaseUint8()
    {
        int length = setAllTestValuesCaseUint8(buffer, 10);

        final VariantWithVariantCaseFW variantWithVariantCase = flyweightRO.wrap(buffer, 10, 10 + length);

        assertSame(flyweightRO, variantWithVariantCase);
    }

    @Test
    public void shouldSetValueUsingSetAsMethod()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsVariantUint8KindOfUint64(0x10)
            .build()
            .limit();

        final VariantWithVariantCaseFW variantWithVariantCase = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(variantWithVariantCase, 0);
    }

    @Test
    public void shouldSetValueUsingSetMethod()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0x10)
            .build()
            .limit();

        final VariantWithVariantCaseFW variantWithVariantCase = flyweightRO.wrap(buffer, 0, limit);

        assertAllTestValuesRead(variantWithVariantCase, 0);
    }
}
