/*
 * Copyright 2021-2026 Aklivity Inc.
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
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.networkOrder.UnionUnsignedFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class UnionUnsignedFWTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(allocateDirect(100));
    private final UnionUnsignedFW.Builder flyweightRW = new UnionUnsignedFW.Builder();
    private final UnionUnsignedFW flyweightRO = new UnionUnsignedFW();

    @Test
    public void shouldSetMaximumUnsigned8()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .unsigned8((byte) 0xFF)
            .build()
            .limit();

        assertEquals(0xFF, flyweightRO.wrap(buffer, 0, limit).unsigned8());
    }

    @Test
    public void shouldSetMaximumUnsigned16()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .unsigned16((short) 0xFFFF)
            .build()
            .limit();

        assertEquals(0xFFFF, flyweightRO.wrap(buffer, 0, limit).unsigned16());
    }

    @Test
    public void shouldSetMaximumUnsigned32()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .unsigned32(0xFFFF_FFFF)
            .build()
            .limit();

        assertEquals(0xFFFF_FFFFL, flyweightRO.wrap(buffer, 0, limit).unsigned32());
    }
}
