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
import java.nio.ByteBuffer;

import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * An {@link UnsafeBuffer} subclass that implements {@link AtomicBufferEx},
 * adding {@link MemorySegment} access to Agrona's {@code Unsafe}-backed buffer.
 * <p>
 * The segment is lazily derived from the underlying byte array or ByteBuffer
 * on each {@code wrap} call, enabling flyweight VarHandle-based field access
 * through {@link DirectBufferEx#segment()} without requiring a full migration
 * to {@link SafeBuffer}.
 */
public class UnsafeBufferEx extends UnsafeBuffer implements AtomicBufferEx
{
    private MemorySegment segment;

    public UnsafeBufferEx()
    {
        super(new byte[0]);
        this.segment = MemorySegment.ofArray(byteArray());
    }

    public UnsafeBufferEx(
        byte[] buffer)
    {
        super(buffer);
        this.segment = MemorySegment.ofArray(buffer);
    }

    public UnsafeBufferEx(
        byte[] buffer,
        int offset,
        int length)
    {
        super(buffer, offset, length);
        this.segment = MemorySegment.ofArray(buffer).asSlice(offset, length);
    }

    public UnsafeBufferEx(
        ByteBuffer buffer)
    {
        super(buffer);
        this.segment = segmentOf(buffer);
    }

    public UnsafeBufferEx(
        ByteBuffer buffer,
        int offset,
        int length)
    {
        super(buffer, offset, length);
        this.segment = segmentOf(buffer).asSlice(offset, length);
    }

    public UnsafeBufferEx(
        DirectBuffer buffer)
    {
        super(buffer);
        this.segment = segmentOf(buffer);
    }

    public UnsafeBufferEx(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        super(buffer, offset, length);
        this.segment = segmentOf(buffer).asSlice(offset, length);
    }

    public UnsafeBufferEx(
        long address,
        int length)
    {
        super(address, length);
        this.segment = MemorySegment.ofAddress(address).reinterpret(length);
    }

    public UnsafeBufferEx(
        MemorySegment segment)
    {
        super(segment.asByteBuffer());
        this.segment = segment;
    }

    public UnsafeBufferEx(
        MemorySegment segment,
        int offset,
        int length)
    {
        super(segment.asSlice(offset, length).asByteBuffer());
        this.segment = segment.asSlice(offset, length);
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
        segment = MemorySegment.ofArray(buffer).asSlice(offset, length);
    }

    @Override
    public void wrap(
        ByteBuffer buffer)
    {
        super.wrap(buffer);
        segment = segmentOf(buffer);
    }

    @Override
    public void wrap(
        ByteBuffer buffer,
        int offset,
        int length)
    {
        super.wrap(buffer, offset, length);
        segment = segmentOf(buffer).asSlice(offset, length);
    }

    @Override
    public void wrap(
        DirectBufferEx buffer)
    {
        super.wrap(buffer);
        segment = buffer.segment();
    }

    @Override
    public void wrap(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        super.wrap(buffer, offset, length);
        segment = buffer.segment().asSlice(offset, length);
    }

    @Override
    public void wrap(
        DirectBuffer buffer)
    {
        super.wrap(buffer);
        segment = segmentOf(buffer);
    }

    @Override
    public void wrap(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        super.wrap(buffer, offset, length);
        segment = segmentOf(buffer).asSlice(offset, length);
    }

    @Override
    public void wrap(
        long address,
        int length)
    {
        super.wrap(address, length);
        segment = MemorySegment.ofAddress(address).reinterpret(length);
    }

    @Override
    public void wrap(
        MemorySegment segment)
    {
        super.wrap(segment.asByteBuffer());
        this.segment = segment;
    }

    @Override
    public void wrap(
        MemorySegment segment,
        int offset,
        int length)
    {
        final MemorySegment sliced = segment.asSlice(offset, length);
        super.wrap(sliced.asByteBuffer());
        this.segment = sliced;
    }

    @Override
    public void getBytes(
        int index,
        MemorySegment dstSegment,
        int dstIndex,
        int length)
    {
        MemorySegment.copy(segment, JAVA_BYTE, index,
            dstSegment, JAVA_BYTE, dstIndex, length);
    }

    @Override
    public void putBytes(
        int index,
        DirectBufferEx srcBuffer,
        int srcIndex,
        int length)
    {
        MemorySegment.copy(srcBuffer.segment(), JAVA_BYTE, srcIndex,
            segment, JAVA_BYTE, index, length);
    }

    @Override
    public void putBytes(
        int index,
        MemorySegment srcSegment,
        int srcIndex,
        int length)
    {
        MemorySegment.copy(srcSegment, JAVA_BYTE, srcIndex,
            segment, JAVA_BYTE, index, length);
    }

    private static MemorySegment segmentOf(
        ByteBuffer buffer)
    {
        return buffer.isDirect()
            ? MemorySegment.ofAddress(BufferUtil.address(buffer)).reinterpret(buffer.capacity())
            : MemorySegment.ofArray(BufferUtil.array(buffer));
    }

    private static MemorySegment segmentOf(
        DirectBuffer buffer)
    {
        if (buffer instanceof DirectBufferEx ex)
        {
            return ex.segment();
        }

        final byte[] array = buffer.byteArray();
        if (array != null)
        {
            final int adjustment = buffer.wrapAdjustment();
            return adjustment == 0 && buffer.capacity() == array.length
                ? MemorySegment.ofArray(array)
                : MemorySegment.ofArray(array).asSlice(adjustment, buffer.capacity());
        }

        final ByteBuffer bb = buffer.byteBuffer();
        if (bb != null)
        {
            return segmentOf(bb);
        }

        return MemorySegment.ofAddress(buffer.addressOffset()).reinterpret(buffer.capacity());
    }
}
