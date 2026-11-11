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

import java.lang.foreign.MemorySegment;

import io.aklivity.zilla.runtime.common.lang.CharSequenceEx.TransferTarget;

/**
 * A {@link TransferTarget} that copies the pieces of a transfer one after another into a {@link MemorySegment},
 * starting at an offset, so a caller that forwards a {@link CharSequenceEx} into memory of its own needs no more
 * than a reusable instance of this. The number of bytes transferred is the value {@code transferTo} returns.
 */
public final class SegmentTransferTarget implements TransferTarget
{
    private MemorySegment destination;
    private long position;

    /**
     * Points this target at {@code destination}, so the next piece is copied to {@code offset}.
     *
     * @param destination  the memory to copy to
     * @param offset       the offset in {@code destination} of the first byte
     *
     * @return this target
     */
    public SegmentTransferTarget wrap(
        MemorySegment destination,
        long offset)
    {
        this.destination = destination;
        this.position = offset;

        return this;
    }

    @Override
    public void accept(
        MemorySegment bytes,
        long offset,
        int length)
    {
        MemorySegment.copy(bytes, offset, destination, position, length);
        position += length;
    }
}
