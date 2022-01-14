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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.OctetsDefaultedNoAnchorFW;

public class OctetsDefaultedNoAnchorFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(150))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final OctetsDefaultedNoAnchorFW octetsDefaultedNoAnchorRO = new OctetsDefaultedNoAnchorFW();


    @Test
    public void shouldWrapWithNullPayload() throws Exception
    {
        buffer.putInt(0,  -1);
        octetsDefaultedNoAnchorRO.wrap(buffer,  0,  4);
        assertNull(octetsDefaultedNoAnchorRO.payload());
        assertEquals(0, octetsDefaultedNoAnchorRO.extension().sizeof());
    }
}
