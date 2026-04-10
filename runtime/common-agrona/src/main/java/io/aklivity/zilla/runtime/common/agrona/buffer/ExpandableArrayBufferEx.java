/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.MemorySegment;

import org.agrona.ExpandableArrayBuffer;

/**
 * An {@link ExpandableArrayBuffer} subclass that implements {@link MutableDirectBufferEx},
 * adding {@link MemorySegment} access to Agrona's expandable heap-backed buffer.
 * <p>
 * The segment is re-derived from the underlying byte array on each {@code wrap}
 * or expansion. Since {@code ExpandableArrayBuffer} is always heap-backed,
 * the segment is always obtained via {@link MemorySegment#ofArray(byte[])}.
 */
public class ExpandableArrayBufferEx extends ExpandableArrayBuffer implements MutableDirectBufferEx
{
    private MemorySegment segment;

    public ExpandableArrayBufferEx()
    {
        super();
        this.segment = MemorySegment.ofArray(byteArray());
    }

    public ExpandableArrayBufferEx(
        int initialCapacity)
    {
        super(initialCapacity);
        this.segment = MemorySegment.ofArray(byteArray());
    }

    @Override
    public MemorySegment segment()
    {
        return segment;
    }

    @Override
    public void wrap(
        byte[] buffer)
    {
        super.wrap(buffer);
        segment = MemorySegment.ofArray(buffer);
    }

    @Override
    public void wrap(
        byte[] buffer,
        int offset,
        int length)
    {
        super.wrap(buffer, offset, length);
        segment = MemorySegment.ofArray(buffer);
    }

    @Override
    public void checkLimit(
        int limit)
    {
        super.checkLimit(limit);
        if (limit > segment.byteSize())
        {
            segment = MemorySegment.ofArray(byteArray());
        }
    }

    @Override
    public void getBytes(
        int index,
        MemorySegment dstSegment,
        int dstIndex,
        int length)
    {
        MemorySegment.copy(segment, JAVA_BYTE, wrapAdjustment() + index,
            dstSegment, JAVA_BYTE, dstIndex, length);
    }

    @Override
    public void putBytes(
        int index,
        DirectBufferEx srcBuffer,
        int srcIndex,
        int length)
    {
        MemorySegment.copy(srcBuffer.segment(), JAVA_BYTE, srcBuffer.wrapAdjustment() + srcIndex,
            segment, JAVA_BYTE, wrapAdjustment() + index, length);
    }

    @Override
    public void putBytes(
        int index,
        MemorySegment srcSegment,
        int srcIndex,
        int length)
    {
        MemorySegment.copy(srcSegment, JAVA_BYTE, srcIndex,
            segment, JAVA_BYTE, wrapAdjustment() + index, length);
    }
}
