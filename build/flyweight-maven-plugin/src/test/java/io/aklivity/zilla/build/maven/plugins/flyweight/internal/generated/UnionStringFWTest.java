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
import static org.junit.Assert.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.networkOrder.UnionStringFW;

public class UnionStringFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final MutableDirectBuffer expected = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final UnionStringFW.Builder flyweightRW = new UnionStringFW.Builder();
    private final UnionStringFW flyweightRO = new UnionStringFW();

    @Test
    public void shouldSetStringWithString8()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .string1("valueOfString1")
            .build()
            .limit();

        final UnionStringFW unionString = flyweightRO.wrap(buffer,  0,  limit);

        assertEquals("valueOfString1", unionString.string1().asString());
    }

    @Test
    public void shouldSetStringWithString16()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .string2("valueOfString2")
            .build()
            .limit();

        final UnionStringFW unionString = flyweightRO.wrap(buffer,  0,  limit);

        assertEquals("valueOfString2", unionString.string2().asString());
    }

    @Test
    public void shouldSetStringWithString32()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .string3("valueOfString3")
            .build()
            .limit();

        final UnionStringFW unionString = flyweightRO.wrap(buffer,  0,  limit);

        assertEquals("valueOfString3", unionString.string3().asString());
    }

    @Test
    public void shouldSetString16BasedOnNetworkOrder()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .string2("valueOfString2")
            .build()
            .limit();

        final UnionStringFW unionString = flyweightRO.wrap(buffer,  0,  limit);

        assertEquals(14, unionString.string2().length());
    }

    @Test
    public void shouldSetString32BasedOnNetworkOrder()
    {
        int limit = flyweightRW.wrap(buffer, 0, buffer.capacity())
            .string3("valueOfString3")
            .build()
            .limit();

        final UnionStringFW unionString = flyweightRO.wrap(buffer,  0,  limit);

        assertEquals(14, unionString.string3().length());
    }
}
