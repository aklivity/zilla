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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.inner.PotentialNameConflictsFW;

public class PotentialNameConflitsFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final PotentialNameConflictsFW.Builder potentialNameConflictsRW = new PotentialNameConflictsFW.Builder();
    private final PotentialNameConflictsFW potentialNameConflictsRO = new PotentialNameConflictsFW();

    @Test
    public void shouldSetAllValues() throws Exception
    {
        int limit = potentialNameConflictsRW.wrap(buffer, 0, buffer.capacity())
                .value("value")
                .newLimit(11)
                .buffer$("buffer")
                .offset$(b -> b.put("1234567890".getBytes(UTF_8)))
                .limit$("limit")
                .build()
                .limit();
        potentialNameConflictsRO.wrap(buffer,  0,  limit);
        assertEquals("value", potentialNameConflictsRO.value().asString());
        assertEquals(11, potentialNameConflictsRO.newLimit());
        final String octetsValue = potentialNameConflictsRO.offset$().get(
            (buffer, offset, limit2) ->  buffer.getStringWithoutLengthUtf8(offset,  limit2 - offset));
        assertEquals("1234567890", octetsValue);
        assertEquals("limit", potentialNameConflictsRO.limit$().asString());
    }

}
