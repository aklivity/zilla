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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.List0FW;

public class List0FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final List0FW list0RO = new List0FW();

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        list0RO.wrap(buffer,  1, 0);
    }

    @Test
    public void shouldNotTryWrapWhenLengthInsufficientForMinimumRequiredLength()
    {
        assertNull(list0RO.tryWrap(buffer,  1, 0));
    }

    @Test
    public void shouldWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 0;
        int fieldCount = 0;
        int offsetLength = 10;
        int maxLimit = offsetLength + length;

        final List0FW list0 = list0RO.wrap(buffer, offsetLength, maxLimit);

        assertSame(list0RO, list0);
        assertEquals(length, list0.length());
        assertEquals(fieldCount, list0.fieldCount());
        assertEquals(0, list0.fields().capacity());
        assertEquals(maxLimit, list0.limit());
    }

    @Test
    public void shouldTryWrapWhenLengthSufficientForMinimumRequiredLength()
    {
        int length = 0;
        int fieldCount = 0;
        int offsetLength = 10;
        int maxLimit = offsetLength + length;

        final List0FW list0 = list0RO.wrap(buffer, offsetLength, maxLimit);

        assertSame(list0RO, list0);
        assertEquals(length, list0.length());
        assertEquals(fieldCount, list0.fieldCount());
        assertEquals(0, list0.fields().capacity());
        assertEquals(maxLimit, list0.limit());
    }
}
