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
package io.aklivity.zilla.runtime.common.agrona.buffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.lang.foreign.MemorySegment;

import org.junit.Test;

public class BufferTransferTargetTest
{
    @Test
    public void shouldWritePiecesOneAfterAnotherFromTheIndex()
    {
        final byte[] source = "abcdefgh".getBytes();
        final MemorySegment segment = MemorySegment.ofArray(source);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[16]);
        final BufferTransferTarget target = new BufferTransferTarget().wrap(buffer, 3);

        target.accept(segment, 1, 3);
        target.accept(segment, 6, 2);

        final byte[] written = new byte[5];
        buffer.getBytes(3, written);

        assertArrayEquals("bcdgh".getBytes(), written);
        assertEquals(5, target.transferred());
    }

    @Test
    public void shouldCountFromTheLatestWrap()
    {
        final MemorySegment segment = MemorySegment.ofArray("abcd".getBytes());
        final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[8]);
        final BufferTransferTarget target = new BufferTransferTarget().wrap(buffer, 0);

        target.accept(segment, 0, 4);
        target.wrap(buffer, 4);

        assertEquals(0, target.transferred());
    }
}
