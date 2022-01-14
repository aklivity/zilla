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
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.TypedefFromTypedefFW;

public class TypedefFromTypedefFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final TypedefFromTypedefFW.Builder flyweightRW = new TypedefFromTypedefFW.Builder();
    private final TypedefFromTypedefFW flyweightRO = new TypedefFromTypedefFW();

    static int setAllTestValues(
        MutableDirectBuffer buffer,
        final int offset)
    {
        int pos = offset;
        buffer.putInt(pos, (int) EnumWithUint32.NI.value());
        buffer.putInt(pos += SIZE_OF_INT, (int) 4000000000L);
        return pos - offset + SIZE_OF_INT;
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
        assertEquals(4000000000L, flyweightRO.getAsUint32());
        assertEquals(4000000000L, flyweightRO.get());
        assertEquals(EnumWithUint32.NI, flyweightRO.kind());
    }

    @Test
    public void shouldWrapAndReadAllValuesCase() throws Exception
    {
        final int offset = 1;
        setAllTestValues(buffer, offset);
        flyweightRO.wrap(buffer, offset, buffer.capacity());
        assertEquals(4000000000L, flyweightRO.getAsUint32());
        assertEquals(4000000000L, flyweightRO.get());
        assertEquals(EnumWithUint32.NI, flyweightRO.kind());
    }

    @Test
    public void shouldSetAsUint32()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .setAsUint32(4000000000L)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(4000000000L, flyweightRO.getAsUint32());
        assertEquals(4000000000L, flyweightRO.get());
        assertEquals(EnumWithUint32.NI, flyweightRO.kind());
    }

    @Test
    public void shouldSetUint32UsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(4000000000L)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(4000000000L, flyweightRO.getAsUint32());
        assertEquals(4000000000L, flyweightRO.get());
        assertEquals(EnumWithUint32.NI, flyweightRO.kind());
    }

    @Test
    public void shouldSetZeroUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(0)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(0, flyweightRO.getAsZero());
        assertEquals(0, flyweightRO.get());
        assertEquals(EnumWithUint32.ICHI, flyweightRO.kind());
    }

    @Test
    public void shouldSetOneUsingSet()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .set(1)
            .build()
            .limit();
        flyweightRO.wrap(buffer, 0, limit);
        assertEquals(1, flyweightRO.getAsOne());
        assertEquals(1, flyweightRO.get());
        assertEquals(EnumWithUint32.SAN, flyweightRO.kind());
    }
}
