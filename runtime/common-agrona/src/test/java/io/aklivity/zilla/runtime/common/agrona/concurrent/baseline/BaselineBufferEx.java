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
package io.aklivity.zilla.runtime.common.agrona.concurrent.baseline;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * The original {@code sun.misc.Unsafe}-backed {@code UnsafeBufferEx}, preserved as a
 * benchmark baseline. It subclasses Agrona's {@link UnsafeBuffer} and lazily derives a
 * {@link MemorySegment} on each {@code wrap}, exactly as the implementation did before the
 * {@code MemorySegment}/GLOBAL rewrite. Keeping it here lets {@code BufferBM} compare the
 * current {@code UnsafeBufferEx} against the old {@code Unsafe} path directly, tracking
 * for regressions without juggling a separate git worktree.
 */
public class BaselineBufferEx extends UnsafeBuffer implements AtomicBufferEx
{
    private MemorySegment segment;

    public BaselineBufferEx()
    {
        super(new byte[0]);
        this.segment = MemorySegment.ofArray(byteArray());
    }

    public BaselineBufferEx(
        byte[] buffer)
    {
        super(buffer);
        this.segment = MemorySegment.ofArray(buffer);
    }

    public BaselineBufferEx(
        byte[] buffer,
        int offset,
        int length)
    {
        super(buffer, offset, length);
        this.segment = MemorySegment.ofArray(buffer);
    }

    public BaselineBufferEx(
        ByteBuffer buffer)
    {
        super(buffer);
        this.segment = segmentOf(buffer);
    }

    public BaselineBufferEx(
        ByteBuffer buffer,
        int offset,
        int length)
    {
        super(buffer, offset, length);
        this.segment = segmentOf(buffer);
    }

    public BaselineBufferEx(
        DirectBuffer buffer)
    {
        super(buffer);
        this.segment = segmentOf(buffer);
    }

    public BaselineBufferEx(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        super(buffer, offset, length);
        this.segment = segmentOf(buffer);
    }

    public BaselineBufferEx(
        long address,
        int length)
    {
        super(address, length);
        this.segment = MemorySegment.ofAddress(address).reinterpret(length);
    }

    public BaselineBufferEx(
        MemorySegment segment)
    {
        super(segment.asByteBuffer());
        this.segment = segment;
    }

    public BaselineBufferEx(
        MemorySegment segment,
        int offset,
        int length)
    {
        super(segment.asByteBuffer(), offset, length);
        this.segment = segment;
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
        segment = segmentOf(buffer);
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
        segment = buffer.segment();
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
        segment = segmentOf(buffer);
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
        super.wrap(segment.asByteBuffer(), offset, length);
        this.segment = segment;
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
            segment, JAVA_BYTE, wrapAdjustment() + index, length);
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
        MemorySegment result;

        if (buffer instanceof DirectBufferEx ex)
        {
            result = ex.segment();
        }
        else
        {
            final byte[] array = buffer.byteArray();
            final ByteBuffer bb = buffer.byteBuffer();
            if (array != null)
            {
                result = MemorySegment.ofArray(array);
            }
            else if (bb != null)
            {
                result = segmentOf(bb);
            }
            else
            {
                result = MemorySegment.ofAddress(buffer.addressOffset()).reinterpret(buffer.capacity());
            }
        }

        return result;
    }
}
