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
import static org.junit.Assert.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.IntegersFW;

public class IntegerFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final IntegersFW.Builder integersRW = new IntegersFW.Builder();
    private final IntegersFW integersRO = new IntegersFW();

    @Test
    public void shouldDefaultValues() throws Exception
    {
        int limit = integersRW.wrap(buffer, 0, 100)
                .build()
                .limit();
        integersRO.wrap(buffer,  0,  limit);
        assertEquals(0xFF, integersRO.unsigned8());
        assertEquals(0xFFFF, integersRO.unsigned16());
        assertEquals(0x7FFFFFFF, integersRO.unsigned32());
        assertEquals(0x7FFFFFFF, integersRO.unsigned64());
        assertEquals(123, integersRO.variable32());
        assertEquals(-8, integersRO.signed8());
        assertEquals(-16, integersRO.signed16());
        assertEquals(-32, integersRO.signed32());
        assertEquals(-64, integersRO.signed64());
        assertEquals(-234, integersRO.variable64());
    }

    @Test
    public void shouldSetUnsigned8ToMaximumValue()
    {
        int limit = integersRW.wrap(buffer, 0, buffer.capacity())
               .unsigned8(0xFF)
               .build()
               .limit();
        integersRO.wrap(buffer,  0,  limit);
        assertEquals(0xFF, integersRO.unsigned8());
    }

    @Test
    public void shouldSetUnsigned8ToMinimumValue()
    {
        int limit = integersRW.wrap(buffer, 0, buffer.capacity())
               .unsigned8(0)
               .build()
               .limit();
        integersRO.wrap(buffer,  0,  limit);
        assertEquals(0, integersRO.unsigned8());
    }

    @Test
    public void shouldSetVarint32ToMaximumValue()
    {
        int limit = integersRW.wrap(buffer, 0, buffer.capacity())
               .variable32(Integer.MAX_VALUE)
               .build()
               .limit();
        integersRO.wrap(buffer,  0,  limit);
        assertEquals(Integer.MAX_VALUE, integersRO.variable32());
    }

    @Test
    public void shouldSetVarint64ToMinimumValue()
    {
        int limit = integersRW.wrap(buffer, 0, buffer.capacity())
                .variable64(Long.MIN_VALUE)
                .build()
                .limit();
        integersRO.wrap(buffer,  0,  limit);
        assertEquals(Long.MIN_VALUE, integersRO.variable64());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUnsigned8WithInsufficientSpace()
    {
        integersRW.wrap(buffer, 10, 10)
               .unsigned8(10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetUnsigned8WithValueTooHigh()
    {
        integersRW.wrap(buffer, 0, buffer.capacity())
               .unsigned8(0xFF + 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetUnsigned8WithValueTooLow()
    {
        integersRW.wrap(buffer, 0, buffer.capacity())
               .unsigned8(-1);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetUnsigned8() throws Exception
    {
        integersRW.wrap(buffer, 0, 100)
            .unsigned8(10)
            .unsigned8(20)
            .build();
    }

    @Test
    public void shouldSetUnsigned16ToMaximumValue()
    {
        int limit = integersRW.wrap(buffer, 0, buffer.capacity())
               .unsigned16(0xFFFF)
               .build()
               .limit();
        integersRO.wrap(buffer,  0,  limit);
        assertEquals(0xFFFF, integersRO.unsigned16());
    }

    @Test
    public void shouldSetUnsigned16ToMinimumValue()
    {
        int limit = integersRW.wrap(buffer, 0, buffer.capacity())
               .unsigned16(0)
               .build()
               .limit();
        integersRO.wrap(buffer,  0,  limit);
        assertEquals(0, integersRO.unsigned16());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUnsigned16WithInsufficientSpace()
    {
        integersRW.wrap(buffer, 10, 12)
            .unsigned16(10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetUnsigned16WithValueTooHigh()
    {
        integersRW.wrap(buffer, 0, buffer.capacity())
            .unsigned16(0xFFFF + 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToSetUnsigned16WithValueTooLow()
    {
        integersRW.wrap(buffer, 0, buffer.capacity())
            .unsigned16(-1);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailToResetUnsigned16() throws Exception
    {
        integersRW.wrap(buffer, 0, 100)
            .unsigned16(10)
            .unsigned16(20)
            .build();
    }


    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToBuildWithInsufficientSpace()
    {
        integersRW.wrap(buffer, 10, 30)
            .build();
    }

    @Test
    public void shouldSetAllValues() throws Exception
    {
        integersRW.wrap(buffer, 0, buffer.capacity())
            .unsigned8(10)
            .unsigned16(20)
            .unsigned32(30)
            .unsigned64(40)
            .signed8((byte) -10)
            .signed16((short) -20)
            .signed32(-30)
            .signed64(-40)
            .build();
        integersRO.wrap(buffer,  0,  100);
        assertEquals(10, integersRO.unsigned8());
    }

}
