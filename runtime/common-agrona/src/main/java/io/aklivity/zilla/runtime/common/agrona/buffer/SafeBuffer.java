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
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;
import static java.nio.ByteOrder.BIG_ENDIAN;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * An {@link AtomicBufferEx} implementation backed by Java's {@link MemorySegment}
 * (Foreign Function and Memory API) instead of {@code sun.misc.Unsafe}.
 * <p>
 * All atomic operations use {@link VarHandle} access modes on the underlying
 * {@code MemorySegment}, providing the same memory ordering guarantees as
 * {@link UnsafeBuffer} without depending on {@code jdk.unsupported}.
 */
public class SafeBuffer implements AtomicBufferEx
{
    private static final ValueLayout.OfByte BYTE_LAYOUT = JAVA_BYTE;
    private static final ValueLayout.OfInt INT_LAYOUT = JAVA_INT_UNALIGNED;
    private static final ValueLayout.OfLong LONG_LAYOUT = JAVA_LONG_UNALIGNED;
    private static final ValueLayout.OfShort SHORT_LAYOUT = JAVA_SHORT_UNALIGNED;

    private static final ValueLayout.OfInt INT_BE =
        JAVA_INT_UNALIGNED.withOrder(BIG_ENDIAN);
    private static final ValueLayout.OfLong LONG_BE =
        JAVA_LONG_UNALIGNED.withOrder(BIG_ENDIAN);
    private static final ValueLayout.OfShort SHORT_BE =
        JAVA_SHORT_UNALIGNED.withOrder(BIG_ENDIAN);

    private static final ValueLayout.OfInt INT_ALIGNED = JAVA_INT;
    private static final ValueLayout.OfLong LONG_ALIGNED = JAVA_LONG;

    private static final VarHandle LONG_HANDLE = LONG_ALIGNED.varHandle();
    private static final VarHandle INT_HANDLE = INT_ALIGNED.varHandle();

    private MemorySegment segment;
    private byte[] byteArray;
    private ByteBuffer byteBuffer;
    private int capacity;
    private int wrapAdjustment;

    public SafeBuffer()
    {
        wrap(new byte[0]);
    }

    public SafeBuffer(
        byte[] buffer)
    {
        wrap(buffer);
    }

    public SafeBuffer(
        byte[] buffer,
        int offset,
        int length)
    {
        wrap(buffer, offset, length);
    }

    public SafeBuffer(
        ByteBuffer buffer)
    {
        wrap(buffer);
    }

    public SafeBuffer(
        ByteBuffer buffer,
        int offset,
        int length)
    {
        wrap(buffer, offset, length);
    }

    public SafeBuffer(
        DirectBuffer buffer)
    {
        wrap(buffer);
    }

    public SafeBuffer(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        wrap(buffer, offset, length);
    }

    public SafeBuffer(
        long address,
        int length)
    {
        wrap(address, length);
    }

    public SafeBuffer(
        MemorySegment segment)
    {
        wrap(segment);
    }

    public SafeBuffer(
        MemorySegment segment,
        int offset,
        int length)
    {
        wrap(segment, offset, length);
    }

    public SafeBuffer(
        Arena arena,
        int capacity)
    {
        wrap(arena.allocate(capacity, Long.BYTES));
    }

    public SafeBuffer asReadOnly()
    {
        return new SafeBuffer(segment.asReadOnly());
    }

    @Override
    public MemorySegment segment()
    {
        return segment;
    }

    // -----------------------------------------------------------------------
    // wrap
    // -----------------------------------------------------------------------

    @Override
    public void wrap(
        byte[] buffer)
    {
        byteArray = buffer;
        byteBuffer = null;
        segment = MemorySegment.ofArray(buffer);
        capacity = buffer.length;
        wrapAdjustment = 0;
    }

    @Override
    public void wrap(
        byte[] buffer,
        int offset,
        int length)
    {
        byteArray = buffer;
        byteBuffer = null;
        segment = MemorySegment.ofArray(buffer).asSlice(offset, length);
        capacity = length;
        wrapAdjustment = offset;
    }

    @Override
    public void wrap(
        ByteBuffer buffer)
    {
        byteBuffer = buffer;
        if (buffer.isDirect())
        {
            byteArray = null;
            segment = MemorySegment.ofAddress(BufferUtil.address(buffer))
                .reinterpret(buffer.capacity());
        }
        else
        {
            byteArray = BufferUtil.array(buffer);
            segment = MemorySegment.ofArray(byteArray);
        }
        capacity = buffer.capacity();
        wrapAdjustment = 0;
    }

    @Override
    public void wrap(
        ByteBuffer buffer,
        int offset,
        int length)
    {
        byteBuffer = buffer;
        if (buffer.isDirect())
        {
            byteArray = null;
            segment = MemorySegment.ofAddress(BufferUtil.address(buffer))
                .reinterpret(buffer.capacity())
                .asSlice(offset, length);
        }
        else
        {
            byteArray = BufferUtil.array(buffer);
            segment = MemorySegment.ofArray(byteArray).asSlice(offset, length);
        }
        capacity = length;
        wrapAdjustment = offset;
    }

    @Override
    public void wrap(
        DirectBuffer buffer)
    {
        if (buffer instanceof SafeBuffer safe)
        {
            segment = safe.segment;
            byteArray = safe.byteArray;
            byteBuffer = safe.byteBuffer;
            capacity = safe.capacity;
            wrapAdjustment = safe.wrapAdjustment;
        }
        else
        {
            wrapFromUnsafe(buffer, 0, buffer.capacity());
        }
    }

    @Override
    public void wrap(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        if (buffer instanceof SafeBuffer safe)
        {
            segment = safe.segment.asSlice(offset, length);
            byteArray = safe.byteArray;
            byteBuffer = safe.byteBuffer;
            capacity = length;
            wrapAdjustment = safe.wrapAdjustment + offset;
        }
        else
        {
            wrapFromUnsafe(buffer, offset, length);
        }
    }

    @Override
    public void wrap(
        long address,
        int length)
    {
        byteArray = null;
        byteBuffer = null;
        segment = MemorySegment.ofAddress(address).reinterpret(length);
        capacity = length;
        wrapAdjustment = 0;
    }

    @Override
    public void wrap(
        MemorySegment segment)
    {
        byteArray = null;
        byteBuffer = null;
        this.segment = segment;
        capacity = (int) segment.byteSize();
        wrapAdjustment = 0;
    }

    @Override
    public void wrap(
        MemorySegment segment,
        int offset,
        int length)
    {
        byteArray = null;
        byteBuffer = null;
        this.segment = segment.asSlice(offset, length);
        capacity = length;
        wrapAdjustment = offset;
    }

    private void wrapFromUnsafe(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        final byte[] array = buffer.byteArray();
        if (array != null)
        {
            final int adjustment = buffer.wrapAdjustment();
            wrap(array, adjustment + offset, length);
        }
        else
        {
            final ByteBuffer bb = buffer.byteBuffer();
            if (bb != null)
            {
                wrap(bb, buffer.wrapAdjustment() + offset, length);
            }
            else
            {
                wrap(buffer.addressOffset() + offset, length);
            }
        }
    }

    // -----------------------------------------------------------------------
    // DirectBuffer accessors
    // -----------------------------------------------------------------------

    @Override
    public long addressOffset()
    {
        return segment.address();
    }

    @Override
    public byte[] byteArray()
    {
        return byteArray;
    }

    @Override
    public ByteBuffer byteBuffer()
    {
        return byteBuffer;
    }

    @Override
    public int capacity()
    {
        return capacity;
    }

    @Override
    public int wrapAdjustment()
    {
        return wrapAdjustment;
    }

    @Override
    public boolean isExpandable()
    {
        return false;
    }

    @Override
    public void checkLimit(
        int limit)
    {
        if (limit > capacity)
        {
            throw new IndexOutOfBoundsException(
                String.format("limit=%d is beyond capacity=%d", limit, capacity));
        }
    }

    @Override
    public void boundsCheck(
        int index,
        int length)
    {
        final long resultingPosition = (long) index + (long) length;
        if (index < 0 || length < 0 || resultingPosition > capacity)
        {
            throw new IndexOutOfBoundsException(
                String.format("index=%d, length=%d, capacity=%d", index, length, capacity));
        }
    }

    @Override
    public void verifyAlignment()
    {
        final long address = segment.address();
        if (0 != (address & 7))
        {
            throw new IllegalStateException(
                String.format("AtomicBuffer is not correctly aligned: address=%d", address));
        }
    }

    // -----------------------------------------------------------------------
    // get — primitives (native byte order)
    // -----------------------------------------------------------------------

    @Override
    public long getLong(
        int index)
    {
        return segment.get(LONG_LAYOUT, index);
    }

    @Override
    public int getInt(
        int index)
    {
        return segment.get(INT_LAYOUT, index);
    }

    @Override
    public short getShort(
        int index)
    {
        return segment.get(SHORT_LAYOUT, index);
    }

    @Override
    public byte getByte(
        int index)
    {
        return segment.get(BYTE_LAYOUT, index);
    }

    @Override
    public double getDouble(
        int index)
    {
        return Double.longBitsToDouble(segment.get(LONG_LAYOUT, index));
    }

    @Override
    public float getFloat(
        int index)
    {
        return Float.intBitsToFloat(segment.get(INT_LAYOUT, index));
    }

    @Override
    public char getChar(
        int index)
    {
        return (char) segment.get(SHORT_LAYOUT, index);
    }

    // -----------------------------------------------------------------------
    // get — primitives (explicit byte order)
    // -----------------------------------------------------------------------

    @Override
    public long getLong(
        int index,
        ByteOrder byteOrder)
    {
        return segment.get(LONG_LAYOUT.withOrder(byteOrder), index);
    }

    @Override
    public int getInt(
        int index,
        ByteOrder byteOrder)
    {
        return segment.get(INT_LAYOUT.withOrder(byteOrder), index);
    }

    @Override
    public short getShort(
        int index,
        ByteOrder byteOrder)
    {
        return segment.get(SHORT_LAYOUT.withOrder(byteOrder), index);
    }

    @Override
    public double getDouble(
        int index,
        ByteOrder byteOrder)
    {
        return Double.longBitsToDouble(segment.get(LONG_LAYOUT.withOrder(byteOrder), index));
    }

    @Override
    public float getFloat(
        int index,
        ByteOrder byteOrder)
    {
        return Float.intBitsToFloat(segment.get(INT_LAYOUT.withOrder(byteOrder), index));
    }

    @Override
    public char getChar(
        int index,
        ByteOrder byteOrder)
    {
        return (char) segment.get(SHORT_LAYOUT.withOrder(byteOrder), index);
    }

    // -----------------------------------------------------------------------
    // put — primitives (native byte order)
    // -----------------------------------------------------------------------

    @Override
    public void putLong(
        int index,
        long value)
    {
        segment.set(LONG_LAYOUT, index, value);
    }

    @Override
    public void putInt(
        int index,
        int value)
    {
        segment.set(INT_LAYOUT, index, value);
    }

    @Override
    public void putShort(
        int index,
        short value)
    {
        segment.set(SHORT_LAYOUT, index, value);
    }

    @Override
    public void putByte(
        int index,
        byte value)
    {
        segment.set(BYTE_LAYOUT, index, value);
    }

    @Override
    public void putDouble(
        int index,
        double value)
    {
        segment.set(LONG_LAYOUT, index, Double.doubleToRawLongBits(value));
    }

    @Override
    public void putFloat(
        int index,
        float value)
    {
        segment.set(INT_LAYOUT, index, Float.floatToRawIntBits(value));
    }

    @Override
    public void putChar(
        int index,
        char value)
    {
        segment.set(SHORT_LAYOUT, index, (short) value);
    }

    // -----------------------------------------------------------------------
    // put — primitives (explicit byte order)
    // -----------------------------------------------------------------------

    @Override
    public void putLong(
        int index,
        long value,
        ByteOrder byteOrder)
    {
        segment.set(LONG_LAYOUT.withOrder(byteOrder), index, value);
    }

    @Override
    public void putInt(
        int index,
        int value,
        ByteOrder byteOrder)
    {
        segment.set(INT_LAYOUT.withOrder(byteOrder), index, value);
    }

    @Override
    public void putShort(
        int index,
        short value,
        ByteOrder byteOrder)
    {
        segment.set(SHORT_LAYOUT.withOrder(byteOrder), index, value);
    }

    @Override
    public void putDouble(
        int index,
        double value,
        ByteOrder byteOrder)
    {
        segment.set(LONG_LAYOUT.withOrder(byteOrder), index, Double.doubleToRawLongBits(value));
    }

    @Override
    public void putFloat(
        int index,
        float value,
        ByteOrder byteOrder)
    {
        segment.set(INT_LAYOUT.withOrder(byteOrder), index, Float.floatToRawIntBits(value));
    }

    @Override
    public void putChar(
        int index,
        char value,
        ByteOrder byteOrder)
    {
        segment.set(SHORT_LAYOUT.withOrder(byteOrder), index, (short) value);
    }

    // -----------------------------------------------------------------------
    // getBytes / putBytes
    // -----------------------------------------------------------------------

    @Override
    public void getBytes(
        int index,
        byte[] dst)
    {
        getBytes(index, dst, 0, dst.length);
    }

    @Override
    public void getBytes(
        int index,
        byte[] dst,
        int offset,
        int length)
    {
        MemorySegment.copy(segment, BYTE_LAYOUT, index,
            MemorySegment.ofArray(dst), BYTE_LAYOUT, offset, length);
    }

    @Override
    public void getBytes(
        int index,
        MutableDirectBuffer dstBuffer,
        int dstIndex,
        int length)
    {
        if (dstBuffer instanceof SafeBuffer safe)
        {
            MemorySegment.copy(segment, index, safe.segment, dstIndex, length);
        }
        else
        {
            final byte[] tmp = new byte[length];
            MemorySegment.copy(segment, BYTE_LAYOUT, index,
                MemorySegment.ofArray(tmp), BYTE_LAYOUT, 0, length);
            dstBuffer.putBytes(dstIndex, tmp, 0, length);
        }
    }

    @Override
    public void getBytes(
        int index,
        ByteBuffer dstBuffer,
        int length)
    {
        getBytes(index, dstBuffer, dstBuffer.position(), length);
        dstBuffer.position(dstBuffer.position() + length);
    }

    @Override
    public void getBytes(
        int index,
        ByteBuffer dstBuffer,
        int dstOffset,
        int length)
    {
        final MemorySegment dst = dstBuffer.isDirect()
            ? MemorySegment.ofAddress(BufferUtil.address(dstBuffer)).reinterpret(dstBuffer.capacity())
            : MemorySegment.ofArray(BufferUtil.array(dstBuffer));
        MemorySegment.copy(segment, index, dst, dstOffset, length);
    }

    @Override
    public void getBytes(
        int index,
        MemorySegment dstSegment,
        int dstIndex,
        int length)
    {
        MemorySegment.copy(segment, index, dstSegment, dstIndex, length);
    }

    @Override
    public void putBytes(
        int index,
        byte[] src)
    {
        putBytes(index, src, 0, src.length);
    }

    @Override
    public void putBytes(
        int index,
        byte[] src,
        int offset,
        int length)
    {
        MemorySegment.copy(MemorySegment.ofArray(src), BYTE_LAYOUT, offset,
            segment, BYTE_LAYOUT, index, length);
    }

    @Override
    public void putBytes(
        int index,
        ByteBuffer srcBuffer,
        int length)
    {
        putBytes(index, srcBuffer, srcBuffer.position(), length);
        srcBuffer.position(srcBuffer.position() + length);
    }

    @Override
    public void putBytes(
        int index,
        ByteBuffer srcBuffer,
        int srcOffset,
        int length)
    {
        final MemorySegment src = srcBuffer.isDirect()
            ? MemorySegment.ofAddress(BufferUtil.address(srcBuffer)).reinterpret(srcBuffer.capacity())
            : MemorySegment.ofArray(BufferUtil.array(srcBuffer));
        MemorySegment.copy(src, srcOffset, segment, index, length);
    }

    @Override
    public void putBytes(
        int index,
        DirectBuffer srcBuffer,
        int srcIndex,
        int length)
    {
        if (srcBuffer instanceof SafeBuffer safe)
        {
            MemorySegment.copy(safe.segment, srcIndex, segment, index, length);
        }
        else
        {
            final byte[] srcArray = srcBuffer.byteArray();
            if (srcArray != null)
            {
                final int adjustment = srcBuffer.wrapAdjustment();
                MemorySegment.copy(
                    MemorySegment.ofArray(srcArray), BYTE_LAYOUT, adjustment + srcIndex,
                    segment, BYTE_LAYOUT, index, length);
            }
            else
            {
                final ByteBuffer srcBb = srcBuffer.byteBuffer();
                if (srcBb != null)
                {
                    final MemorySegment src = srcBb.isDirect()
                        ? MemorySegment.ofAddress(BufferUtil.address(srcBb)).reinterpret(srcBb.capacity())
                        : MemorySegment.ofArray(BufferUtil.array(srcBb));
                    MemorySegment.copy(src, srcBuffer.wrapAdjustment() + srcIndex,
                        segment, index, length);
                }
                else
                {
                    for (int i = 0; i < length; i++)
                    {
                        segment.set(BYTE_LAYOUT, index + i, srcBuffer.getByte(srcIndex + i));
                    }
                }
            }
        }
    }

    @Override
    public void putBytes(
        int index,
        MemorySegment srcSegment,
        int srcIndex,
        int length)
    {
        MemorySegment.copy(srcSegment, srcIndex, segment, index, length);
    }

    @Override
    public void setMemory(
        int index,
        int length,
        byte value)
    {
        segment.asSlice(index, length).fill(value);
    }

    // -----------------------------------------------------------------------
    // Atomic — long volatile / ordered / opaque
    // -----------------------------------------------------------------------

    @Override
    public long getLongVolatile(
        int index)
    {
        return (long) LONG_HANDLE.getVolatile(segment, (long) index);
    }

    @Override
    public void putLongVolatile(
        int index,
        long value)
    {
        LONG_HANDLE.setVolatile(segment, (long) index, value);
    }

    @Override
    public void putLongOrdered(
        int index,
        long value)
    {
        LONG_HANDLE.setRelease(segment, (long) index, value);
    }

    @Override
    public long addLongOrdered(
        int index,
        long increment)
    {
        final long currentValue = (long) LONG_HANDLE.getAcquire(segment, (long) index);
        LONG_HANDLE.setRelease(segment, (long) index, currentValue + increment);
        return currentValue;
    }

    @Override
    public boolean compareAndSetLong(
        int index,
        long expectedValue,
        long updateValue)
    {
        return LONG_HANDLE.compareAndSet(segment, (long) index, expectedValue, updateValue);
    }

    @Override
    public long getAndSetLong(
        int index,
        long value)
    {
        return (long) LONG_HANDLE.getAndSet(segment, (long) index, value);
    }

    @Override
    public long getAndAddLong(
        int index,
        long delta)
    {
        return (long) LONG_HANDLE.getAndAdd(segment, (long) index, delta);
    }

    // -----------------------------------------------------------------------
    // Atomic — int volatile / ordered / opaque
    // -----------------------------------------------------------------------

    @Override
    public int getIntVolatile(
        int index)
    {
        return (int) INT_HANDLE.getVolatile(segment, (long) index);
    }

    @Override
    public void putIntVolatile(
        int index,
        int value)
    {
        INT_HANDLE.setVolatile(segment, (long) index, value);
    }

    @Override
    public void putIntOrdered(
        int index,
        int value)
    {
        INT_HANDLE.setRelease(segment, (long) index, value);
    }

    @Override
    public int addIntOrdered(
        int index,
        int increment)
    {
        final int currentValue = (int) INT_HANDLE.getAcquire(segment, (long) index);
        INT_HANDLE.setRelease(segment, (long) index, currentValue + increment);
        return currentValue;
    }

    @Override
    public boolean compareAndSetInt(
        int index,
        int expectedValue,
        int updateValue)
    {
        return INT_HANDLE.compareAndSet(segment, (long) index, expectedValue, updateValue);
    }

    @Override
    public int getAndSetInt(
        int index,
        int value)
    {
        return (int) INT_HANDLE.getAndSet(segment, (long) index, value);
    }

    @Override
    public int getAndAddInt(
        int index,
        int delta)
    {
        return (int) INT_HANDLE.getAndAdd(segment, (long) index, delta);
    }

    // -----------------------------------------------------------------------
    // Atomic — short / char / byte volatile
    // -----------------------------------------------------------------------

    @Override
    public short getShortVolatile(
        int index)
    {
        // No VarHandle for short on MemorySegment; use int volatile and mask
        return (short) segment.get(SHORT_LAYOUT, index);
    }

    @Override
    public void putShortVolatile(
        int index,
        short value)
    {
        segment.set(SHORT_LAYOUT, index, value);
    }

    @Override
    public char getCharVolatile(
        int index)
    {
        return (char) segment.get(SHORT_LAYOUT, index);
    }

    @Override
    public void putCharVolatile(
        int index,
        char value)
    {
        segment.set(SHORT_LAYOUT, index, (short) value);
    }

    @Override
    public byte getByteVolatile(
        int index)
    {
        return segment.get(BYTE_LAYOUT, index);
    }

    @Override
    public void putByteVolatile(
        int index,
        byte value)
    {
        segment.set(BYTE_LAYOUT, index, value);
    }

    // -----------------------------------------------------------------------
    // Atomic — acquire / release / opaque (Agrona 2.x additions)
    // -----------------------------------------------------------------------

    @Override
    public long getLongAcquire(
        int index)
    {
        return (long) LONG_HANDLE.getAcquire(segment, (long) index);
    }

    @Override
    public void putLongRelease(
        int index,
        long value)
    {
        LONG_HANDLE.setRelease(segment, (long) index, value);
    }

    @Override
    public long getLongOpaque(
        int index)
    {
        return (long) LONG_HANDLE.getOpaque(segment, (long) index);
    }

    @Override
    public void putLongOpaque(
        int index,
        long value)
    {
        LONG_HANDLE.setOpaque(segment, (long) index, value);
    }

    @Override
    public long addLongRelease(
        int index,
        long increment)
    {
        final long currentValue = (long) LONG_HANDLE.getAcquire(segment, (long) index);
        LONG_HANDLE.setRelease(segment, (long) index, currentValue + increment);
        return currentValue;
    }

    @Override
    public long addLongOpaque(
        int index,
        long increment)
    {
        final long currentValue = (long) LONG_HANDLE.getOpaque(segment, (long) index);
        LONG_HANDLE.setOpaque(segment, (long) index, currentValue + increment);
        return currentValue;
    }

    @Override
    public long compareAndExchangeLong(
        int index,
        long expectedValue,
        long updateValue)
    {
        return (long) LONG_HANDLE.compareAndExchange(segment, (long) index, expectedValue, updateValue);
    }

    @Override
    public int getIntAcquire(
        int index)
    {
        return (int) INT_HANDLE.getAcquire(segment, (long) index);
    }

    @Override
    public void putIntRelease(
        int index,
        int value)
    {
        INT_HANDLE.setRelease(segment, (long) index, value);
    }

    @Override
    public int getIntOpaque(
        int index)
    {
        return (int) INT_HANDLE.getOpaque(segment, (long) index);
    }

    @Override
    public void putIntOpaque(
        int index,
        int value)
    {
        INT_HANDLE.setOpaque(segment, (long) index, value);
    }

    @Override
    public int addIntRelease(
        int index,
        int increment)
    {
        final int currentValue = (int) INT_HANDLE.getAcquire(segment, (long) index);
        INT_HANDLE.setRelease(segment, (long) index, currentValue + increment);
        return currentValue;
    }

    @Override
    public int addIntOpaque(
        int index,
        int increment)
    {
        final int currentValue = (int) INT_HANDLE.getOpaque(segment, (long) index);
        INT_HANDLE.setOpaque(segment, (long) index, currentValue + increment);
        return currentValue;
    }

    @Override
    public int compareAndExchangeInt(
        int index,
        int expectedValue,
        int updateValue)
    {
        return (int) INT_HANDLE.compareAndExchange(segment, (long) index, expectedValue, updateValue);
    }

    // -----------------------------------------------------------------------
    // String — ASCII (length-prefixed)
    // -----------------------------------------------------------------------

    @Override
    public String getStringAscii(
        int index)
    {
        final int length = segment.get(INT_LAYOUT, index);
        return getStringWithoutLengthAscii(index + Integer.BYTES, length);
    }

    @Override
    public String getStringAscii(
        int index,
        ByteOrder byteOrder)
    {
        final int length = segment.get(INT_LAYOUT.withOrder(byteOrder), index);
        return getStringWithoutLengthAscii(index + Integer.BYTES, length);
    }

    @Override
    public String getStringAscii(
        int index,
        int length)
    {
        return getStringWithoutLengthAscii(index + Integer.BYTES, length);
    }

    @Override
    public int getStringAscii(
        int index,
        Appendable appendable)
    {
        final int length = segment.get(INT_LAYOUT, index);
        return getStringWithoutLengthAscii(index + Integer.BYTES, length, appendable);
    }

    @Override
    public int getStringAscii(
        int index,
        Appendable appendable,
        ByteOrder byteOrder)
    {
        final int length = segment.get(INT_LAYOUT.withOrder(byteOrder), index);
        return getStringWithoutLengthAscii(index + Integer.BYTES, length, appendable);
    }

    @Override
    public int getStringAscii(
        int index,
        int length,
        Appendable appendable)
    {
        return getStringWithoutLengthAscii(index + Integer.BYTES, length, appendable);
    }

    // -----------------------------------------------------------------------
    // String — ASCII (no length prefix)
    // -----------------------------------------------------------------------

    @Override
    public String getStringWithoutLengthAscii(
        int index,
        int length)
    {
        final byte[] bytes = new byte[length];
        MemorySegment.copy(segment, BYTE_LAYOUT, index,
            MemorySegment.ofArray(bytes), BYTE_LAYOUT, 0, length);
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    @Override
    public int getStringWithoutLengthAscii(
        int index,
        int length,
        Appendable appendable)
    {
        try
        {
            for (int i = 0; i < length; i++)
            {
                appendable.append((char) segment.get(BYTE_LAYOUT, index + i));
            }
        }
        catch (java.io.IOException ex)
        {
            throw new RuntimeException(ex);
        }
        return length;
    }

    // -----------------------------------------------------------------------
    // String — UTF-8
    // -----------------------------------------------------------------------

    @Override
    public String getStringUtf8(
        int index)
    {
        final int length = segment.get(INT_LAYOUT, index);
        return getStringWithoutLengthUtf8(index + Integer.BYTES, length);
    }

    @Override
    public String getStringUtf8(
        int index,
        ByteOrder byteOrder)
    {
        final int length = segment.get(INT_LAYOUT.withOrder(byteOrder), index);
        return getStringWithoutLengthUtf8(index + Integer.BYTES, length);
    }

    @Override
    public String getStringUtf8(
        int index,
        int length)
    {
        return getStringWithoutLengthUtf8(index + Integer.BYTES, length);
    }

    @Override
    public String getStringWithoutLengthUtf8(
        int index,
        int length)
    {
        final byte[] bytes = new byte[length];
        MemorySegment.copy(segment, BYTE_LAYOUT, index,
            MemorySegment.ofArray(bytes), BYTE_LAYOUT, 0, length);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // putString — ASCII (length-prefixed)
    // -----------------------------------------------------------------------

    @Override
    public int putStringAscii(
        int index,
        String value)
    {
        final int length = value != null ? value.length() : 0;
        segment.set(INT_LAYOUT, index, length);
        return Integer.BYTES + putStringWithoutLengthAscii(index + Integer.BYTES, value);
    }

    @Override
    public int putStringAscii(
        int index,
        CharSequence value)
    {
        final int length = value != null ? value.length() : 0;
        segment.set(INT_LAYOUT, index, length);
        return Integer.BYTES + putStringWithoutLengthAscii(index + Integer.BYTES, value);
    }

    @Override
    public int putStringAscii(
        int index,
        String value,
        ByteOrder byteOrder)
    {
        final int length = value != null ? value.length() : 0;
        segment.set(INT_LAYOUT.withOrder(byteOrder), index, length);
        return Integer.BYTES + putStringWithoutLengthAscii(index + Integer.BYTES, value);
    }

    @Override
    public int putStringAscii(
        int index,
        CharSequence value,
        ByteOrder byteOrder)
    {
        final int length = value != null ? value.length() : 0;
        segment.set(INT_LAYOUT.withOrder(byteOrder), index, length);
        return Integer.BYTES + putStringWithoutLengthAscii(index + Integer.BYTES, value);
    }

    // -----------------------------------------------------------------------
    // putString — ASCII (no length prefix)
    // -----------------------------------------------------------------------

    @Override
    public int putStringWithoutLengthAscii(
        int index,
        String value)
    {
        if (value == null)
        {
            return 0;
        }
        final int length = value.length();
        for (int i = 0; i < length; i++)
        {
            segment.set(BYTE_LAYOUT, index + i, (byte) value.charAt(i));
        }
        return length;
    }

    @Override
    public int putStringWithoutLengthAscii(
        int index,
        CharSequence value)
    {
        if (value == null)
        {
            return 0;
        }
        final int length = value.length();
        for (int i = 0; i < length; i++)
        {
            segment.set(BYTE_LAYOUT, index + i, (byte) value.charAt(i));
        }
        return length;
    }

    @Override
    public int putStringWithoutLengthAscii(
        int index,
        String value,
        int valueOffset,
        int length)
    {
        if (value == null)
        {
            return 0;
        }
        for (int i = 0; i < length; i++)
        {
            segment.set(BYTE_LAYOUT, index + i, (byte) value.charAt(valueOffset + i));
        }
        return length;
    }

    @Override
    public int putStringWithoutLengthAscii(
        int index,
        CharSequence value,
        int valueOffset,
        int length)
    {
        if (value == null)
        {
            return 0;
        }
        for (int i = 0; i < length; i++)
        {
            segment.set(BYTE_LAYOUT, index + i, (byte) value.charAt(valueOffset + i));
        }
        return length;
    }

    // -----------------------------------------------------------------------
    // putString — UTF-8
    // -----------------------------------------------------------------------

    @Override
    public int putStringUtf8(
        int index,
        String value)
    {
        return putStringUtf8(index, value, ByteOrder.nativeOrder(), Integer.MAX_VALUE);
    }

    @Override
    public int putStringUtf8(
        int index,
        String value,
        ByteOrder byteOrder)
    {
        return putStringUtf8(index, value, byteOrder, Integer.MAX_VALUE);
    }

    @Override
    public int putStringUtf8(
        int index,
        String value,
        int maxEncodedLength)
    {
        return putStringUtf8(index, value, ByteOrder.nativeOrder(), maxEncodedLength);
    }

    @Override
    public int putStringUtf8(
        int index,
        String value,
        ByteOrder byteOrder,
        int maxEncodedLength)
    {
        final byte[] bytes = value != null
            ? value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            : new byte[0];
        final int length = Math.min(bytes.length, maxEncodedLength);
        segment.set(INT_LAYOUT.withOrder(byteOrder), index, length);
        MemorySegment.copy(MemorySegment.ofArray(bytes), BYTE_LAYOUT, 0,
            segment, BYTE_LAYOUT, index + Integer.BYTES, length);
        return Integer.BYTES + length;
    }

    @Override
    public int putStringWithoutLengthUtf8(
        int index,
        String value)
    {
        final byte[] bytes = value != null
            ? value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            : new byte[0];
        MemorySegment.copy(MemorySegment.ofArray(bytes), BYTE_LAYOUT, 0,
            segment, BYTE_LAYOUT, index, bytes.length);
        return bytes.length;
    }

    // -----------------------------------------------------------------------
    // ASCII int/long parsing and encoding
    // -----------------------------------------------------------------------

    @Override
    public int parseNaturalIntAscii(
        int index,
        int length)
    {
        int result = 0;
        for (int i = 0; i < length; i++)
        {
            final byte b = segment.get(BYTE_LAYOUT, index + i);
            result = result * 10 + (b - '0');
        }
        return result;
    }

    @Override
    public long parseNaturalLongAscii(
        int index,
        int length)
    {
        long result = 0;
        for (int i = 0; i < length; i++)
        {
            final byte b = segment.get(BYTE_LAYOUT, index + i);
            result = result * 10 + (b - '0');
        }
        return result;
    }

    @Override
    public int parseIntAscii(
        int index,
        int length)
    {
        if (length == 0)
        {
            return 0;
        }
        final boolean negative = segment.get(BYTE_LAYOUT, index) == '-';
        final int start = negative ? index + 1 : index;
        final int digits = negative ? length - 1 : length;
        final int value = parseNaturalIntAscii(start, digits);
        return negative ? -value : value;
    }

    @Override
    public long parseLongAscii(
        int index,
        int length)
    {
        if (length == 0)
        {
            return 0;
        }
        final boolean negative = segment.get(BYTE_LAYOUT, index) == '-';
        final int start = negative ? index + 1 : index;
        final int digits = negative ? length - 1 : length;
        final long value = parseNaturalLongAscii(start, digits);
        return negative ? -value : value;
    }

    @Override
    public int putIntAscii(
        int index,
        int value)
    {
        return putStringWithoutLengthAscii(index, Integer.toString(value));
    }

    @Override
    public int putNaturalIntAscii(
        int index,
        int value)
    {
        return putStringWithoutLengthAscii(index, Integer.toUnsignedString(value));
    }

    @Override
    public void putNaturalPaddedIntAscii(
        int index,
        int length,
        int value)
    {
        final String str = Integer.toUnsignedString(value);
        final int padding = length - str.length();
        if (padding < 0)
        {
            throw new NumberFormatException(
                String.format("value=%d too large for length=%d", value, length));
        }
        for (int i = 0; i < padding; i++)
        {
            segment.set(BYTE_LAYOUT, index + i, (byte) '0');
        }
        putStringWithoutLengthAscii(index + padding, str);
    }

    @Override
    public int putNaturalIntAsciiFromEnd(
        int value,
        int endExclusive)
    {
        int index = endExclusive;
        int remaining = value;
        do
        {
            index--;
            final int digit = remaining % 10;
            remaining = remaining / 10;
            segment.set(BYTE_LAYOUT, index, (byte) ('0' + digit));
        }
        while (remaining > 0);
        return index;
    }

    @Override
    public int putNaturalLongAscii(
        int index,
        long value)
    {
        return putStringWithoutLengthAscii(index, Long.toUnsignedString(value));
    }

    @Override
    public int putLongAscii(
        int index,
        long value)
    {
        return putStringWithoutLengthAscii(index, Long.toString(value));
    }

    // -----------------------------------------------------------------------
    // Comparable
    // -----------------------------------------------------------------------

    @Override
    public int compareTo(
        DirectBuffer that)
    {
        final int thisCapacity = this.capacity;
        final int thatCapacity = that.capacity();
        final int limit = Math.min(thisCapacity, thatCapacity);

        for (int i = 0; i < limit; i++)
        {
            final int cmp = Byte.compare(this.getByte(i), that.getByte(i));
            if (cmp != 0)
            {
                return cmp;
            }
        }

        return Integer.compare(thisCapacity, thatCapacity);
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    @Override
    public String toString()
    {
        return "SafeBuffer{capacity=" + capacity + "}";
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof DirectBuffer that))
        {
            return false;
        }
        if (this.capacity != that.capacity())
        {
            return false;
        }
        for (int i = 0; i < capacity; i++)
        {
            if (this.getByte(i) != that.getByte(i))
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        int result = 1;
        for (int i = 0; i < capacity; i++)
        {
            result = 31 * result + getByte(i);
        }
        return result;
    }
}
