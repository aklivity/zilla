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

import org.agrona.BufferUtil;
import org.agrona.ExpandableDirectByteBuffer;

/**
 * An {@link ExpandableDirectByteBuffer} subclass that implements {@link MutableDirectBufferEx},
 * adding {@link MemorySegment} access to Agrona's expandable direct-memory buffer.
 * <p>
 * The segment is derived lazily from the underlying {@link java.nio.ByteBuffer}
 * since expansions may reallocate it.
 */
public class ExpandableDirectByteBufferEx extends ExpandableDirectByteBuffer implements MutableDirectBufferEx
{
    public ExpandableDirectByteBufferEx()
    {
        super();
    }

    public ExpandableDirectByteBufferEx(
        int initialCapacity)
    {
        super(initialCapacity);
    }

    @Override
    public MemorySegment segment()
    {
        return MemorySegment.ofAddress(BufferUtil.address(byteBuffer())).reinterpret(capacity());
    }

    @Override
    public void wrap(
        DirectBufferEx buffer)
    {
        super.wrap(buffer.byteBuffer(), 0, buffer.capacity());
    }

    @Override
    public void wrap(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        super.wrap(buffer.byteBuffer(), offset, length);
    }

    @Override
    public void wrap(
        MemorySegment segment)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void wrap(
        MemorySegment segment,
        int offset,
        int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getBytes(
        int index,
        MemorySegment dstSegment,
        int dstIndex,
        int length)
    {
        MemorySegment.copy(segment(), JAVA_BYTE, wrapAdjustment() + index,
            dstSegment, JAVA_BYTE, dstIndex, length);
    }

    @Override
    public void putBytes(
        int index,
        DirectBufferEx srcBuffer,
        int srcIndex,
        int length)
    {
        super.putBytes(index, srcBuffer, srcIndex, length);
    }

    @Override
    public void putBytes(
        int index,
        MemorySegment srcSegment,
        int srcIndex,
        int length)
    {
        MemorySegment.copy(srcSegment, JAVA_BYTE, srcIndex,
            segment(), JAVA_BYTE, wrapAdjustment() + index, length);
    }
}
