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

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.EnumWithInt8;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantEnumKindWithInt32FW;

public class VariantEnumKindWithInt32FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final VariantEnumKindWithInt32FW.Builder flyweightRW = new VariantEnumKindWithInt32FW.Builder();
    private final VariantEnumKindWithInt32FW flyweightRO = new VariantEnumKindWithInt32FW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        int pos = offset;
        buffer.putByte(pos, (byte) 1);
        buffer.putByte(pos += 1, (byte) 120);
        return pos - offset + SIZE_OF_BYTE;
    }

    @Test
    public void shouldNotTryWrapWhenIncomplete()
    {
        int size = setAllTestValues(buffer, 10);
        for (int maxLimit = 10; maxLimit < 10 + size; maxLimit++)
        {
            assertNull(flyweightRO.tryWrap(buffer,  10, maxLimit));
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
        int size = setAllTestValues(buffer, 10);
        assertSame(flyweightRO, flyweightRO.tryWrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldWrapWhenLengthSufficientCase()
    {
        int size = setAllTestValues(buffer, 10);
        assertSame(flyweightRO, flyweightRO.wrap(buffer, 10, 10 + size));
    }

    @Test
    public void shouldTryWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);
        assertNotNull(flyweightRO.tryWrap(buffer, offset, buffer.capacity()));
        assertEquals(120, flyweightRO.getAsInt8());
        assertEquals(120, flyweightRO.get());
        assertEquals(EnumWithInt8.ONE, flyweightRO.kind());
    }

    @Test
    public void shouldWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);
        flyweightRO.wrap(buffer, offset, buffer.capacity());
        assertEquals(120, flyweightRO.getAsInt8());
        assertEquals(120, flyweightRO.get());
        assertEquals(EnumWithInt8.ONE, flyweightRO.kind());
    }

    @Test
    public void shouldSetAsInt32()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsInt32(1000000000)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(1000000000, flyweightRO.getAsInt32());
        assertEquals(1000000000, flyweightRO.get());
        assertEquals(EnumWithInt8.THREE, flyweightRO.kind());
    }

    @Test
    public void shouldSetAsInt16()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsInt16(30000)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(30000, flyweightRO.getAsInt16());
        assertEquals(30000, flyweightRO.get());
        assertEquals(EnumWithInt8.TWO, flyweightRO.kind());
    }

    @Test
    public void shouldSetAsInt8()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsInt8(100)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(100, flyweightRO.getAsInt8());
        assertEquals(100, flyweightRO.get());
        assertEquals(EnumWithInt8.ONE, flyweightRO.kind());
    }

    @Test
    public void shouldSetInt32UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(2000000000)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(2000000000, flyweightRO.getAsInt32());
        assertEquals(2000000000, flyweightRO.get());
        assertEquals(EnumWithInt8.THREE, flyweightRO.kind());
    }

    @Test
    public void shouldSetInt16UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(30000)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(30000, flyweightRO.getAsInt16());
        assertEquals(30000, flyweightRO.get());
        assertEquals(EnumWithInt8.TWO, flyweightRO.kind());
    }

    @Test
    public void shouldSetInt8UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(100)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(100, flyweightRO.getAsInt8());
        assertEquals(100, flyweightRO.get());
        assertEquals(EnumWithInt8.ONE, flyweightRO.kind());
    }

    @Test
    public void shouldSetNegativeInt32ValueUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(-2000000000)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(-2000000000, flyweightRO.getAsInt32());
        assertEquals(-2000000000, flyweightRO.get());
        assertEquals(EnumWithInt8.THREE, flyweightRO.kind());
    }

    @Test
    public void shouldSetNegativeInt16ValueUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(-30000)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(-30000, flyweightRO.getAsInt16());
        assertEquals(-30000, flyweightRO.get());
        assertEquals(EnumWithInt8.TWO, flyweightRO.kind());
    }

    @Test
    public void shouldSetNegativeInt8ValueUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(-100)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(-100, flyweightRO.getAsInt8());
        assertEquals(-100, flyweightRO.get());
        assertEquals(EnumWithInt8.ONE, flyweightRO.kind());
    }
}
