/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.common.lang;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.Test;

public class SegmentTransferTargetTest
{
    @Test
    void shouldCopyPiecesOneAfterAnotherFromTheOffset()
    {
        final MemorySegment source = MemorySegment.ofArray("abcdefgh".getBytes());
        final byte[] bytes = new byte[10];
        final SegmentTransferTarget target = new SegmentTransferTarget().wrap(MemorySegment.ofArray(bytes), 3);

        target.accept(source, 1, 3);
        target.accept(source, 6, 2);

        assertArrayEquals(new byte[] {0, 0, 0, 'b', 'c', 'd', 'g', 'h', 0, 0}, bytes);
    }

    @Test
    void shouldContinueFromTheLatestWrap()
    {
        final MemorySegment source = MemorySegment.ofArray("abcd".getBytes());
        final byte[] bytes = new byte[6];
        final SegmentTransferTarget target = new SegmentTransferTarget().wrap(MemorySegment.ofArray(bytes), 0);

        target.accept(source, 0, 2);
        target.wrap(MemorySegment.ofArray(bytes), 4).accept(source, 2, 2);

        assertArrayEquals(new byte[] {'a', 'b', 0, 0, 'c', 'd'}, bytes);
    }

    @Test
    void shouldNotWritePastTheDestination()
    {
        final MemorySegment source = MemorySegment.ofArray("abcd".getBytes());
        final SegmentTransferTarget target = new SegmentTransferTarget().wrap(MemorySegment.ofArray(new byte[3]), 2);

        assertThrows(IndexOutOfBoundsException.class, () -> target.accept(source, 0, 2));
    }
}
