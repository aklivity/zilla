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
import java.util.Objects;

import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * An {@link AtomicBufferEx} implementation backed by Java's {@link MemorySegment}
 * (Foreign Function and Memory API) instead of {@code sun.misc.Unsafe}.
 * <p>
 * For off-heap (native) memory this buffer accesses bytes through a process-wide
 * {@code GLOBAL} segment that spans the entire address space, enabling unchecked
 * access whose bounds checks the JIT can elide — matching {@code sun.misc.Unsafe}
 * performance without depending on {@code jdk.unsupported}. For heap memory it
 * falls back to the bounded per-instance segment.
 * <p>
 * Native-path bounds checks are enabled when this class is loaded from the class
 * path (unnamed module), as in unit and integration tests, and disabled when it
 * is loaded as a named module, as in the packaged production runtime.
 * <p>
 * All atomic operations use {@link VarHandle} access modes on the selected
 * segment, providing the same memory ordering guarantees as an
 * {@code Unsafe}-backed buffer.
 * <p>
 * Atomic operations are supported only on native-backed buffers (direct
 * {@code ByteBuffer}, mapped file, or native {@code MemorySegment}). Heap
 * {@code byte[]} backings throw {@link UnsupportedOperationException} for
 * atomics, because the JVM cannot guarantee {@code byte[]} alignment for
 * {@code VarHandle} atomic access.
 */
public class UnsafeBufferEx implements AtomicBufferEx
{
    private static final MemorySegment GLOBAL = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);

    // Bounds checks are enabled when this class loads from the class path (unnamed module) — i.e. unit and
    // integration tests — and disabled when it loads as a named module, which is how the packaged jlink
    // runtime loads it in production. Tests keep IndexOutOfBoundsException safety; production takes the
    // unchecked native GLOBAL fast path. Resolved once at class init so the JIT folds the per-access branch.
    private static final boolean SHOULD_BOUNDS_CHECK = !UnsafeBufferEx.class.getModule().isNamed();

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

    private static final ValueLayout.OfInt INT_LE =
        JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfLong LONG_LE =
        JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfShort SHORT_LE =
        JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    private static final ValueLayout.OfInt INT_ALIGNED = JAVA_INT;
    private static final ValueLayout.OfLong LONG_ALIGNED = JAVA_LONG;

    private static final VarHandle LONG_HANDLE = LONG_ALIGNED.varHandle();
    private static final VarHandle INT_HANDLE = INT_ALIGNED.varHandle();

    private static ValueLayout.OfLong longLayout(
        ByteOrder byteOrder)
    {
        return byteOrder == BIG_ENDIAN ? LONG_BE : LONG_LE;
    }

    private static ValueLayout.OfInt intLayout(
        ByteOrder byteOrder)
    {
        return byteOrder == BIG_ENDIAN ? INT_BE : INT_LE;
    }

    private static ValueLayout.OfShort shortLayout(
        ByteOrder byteOrder)
    {
        return byteOrder == BIG_ENDIAN ? SHORT_BE : SHORT_LE;
    }

    private MemorySegment segment;
    private byte[] byteArray;
    private ByteBuffer byteBuffer;
    private long addressOffset;
    private int capacity;
    private int wrapAdjustment;
    private boolean isNative;

    public UnsafeBufferEx()
    {
        wrap(new byte[0]);
    }

    public UnsafeBufferEx(
        byte[] buffer)
    {
        wrap(buffer);
    }

    public UnsafeBufferEx(
        byte[] buffer,
        int offset,
        int length)
    {
        wrap(buffer, offset, length);
    }

    public UnsafeBufferEx(
        ByteBuffer buffer)
    {
        wrap(buffer);
    }

    public UnsafeBufferEx(
        ByteBuffer buffer,
        int offset,
        int length)
    {
        wrap(buffer, offset, length);
    }

    public UnsafeBufferEx(
        DirectBufferEx buffer)
    {
        wrap(buffer);
    }

    public UnsafeBufferEx(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        wrap(buffer, offset, length);
    }

    public UnsafeBufferEx(
        long address,
        int length)
    {
        wrap(address, length);
    }

    public UnsafeBufferEx(
        MemorySegment segment)
    {
        wrap(segment);
    }

    public UnsafeBufferEx(
        MemorySegment segment,
        int offset,
        int length)
    {
        wrap(segment, offset, length);
    }

    public UnsafeBufferEx(
        Arena arena,
        int capacity)
    {
        wrap(arena.allocate(capacity, Long.BYTES));
    }

    public UnsafeBufferEx asReadOnly()
    {
        return new UnsafeBufferEx(segment.asReadOnly());
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
        addressOffset = BufferUtil.ARRAY_BASE_OFFSET;
        capacity = buffer.length;
        wrapAdjustment = 0;
        isNative = false;
    }

    @Override
    public void wrap(
        byte[] buffer,
        int offset,
        int length)
    {
        byteArray = buffer;
        byteBuffer = null;
        segment = MemorySegment.ofArray(buffer);
        addressOffset = BufferUtil.ARRAY_BASE_OFFSET + offset;
        capacity = length;
        wrapAdjustment = offset;
        isNative = false;
    }

    @Override
    public void wrap(
        ByteBuffer buffer)
    {
        byteBuffer = buffer;
        if (buffer.isDirect())
        {
            byteArray = null;
            addressOffset = BufferUtil.address(buffer);
            segment = MemorySegment.ofAddress(addressOffset)
                .reinterpret(buffer.capacity());
        }
        else
        {
            byteArray = BufferUtil.array(buffer);
            addressOffset = BufferUtil.ARRAY_BASE_OFFSET + buffer.arrayOffset();
            segment = MemorySegment.ofArray(byteArray);
        }
        capacity = buffer.capacity();
        wrapAdjustment = 0;
        isNative = buffer.isDirect();
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
            addressOffset = BufferUtil.address(buffer) + offset;
            segment = MemorySegment.ofAddress(BufferUtil.address(buffer))
                .reinterpret(buffer.capacity());
        }
        else
        {
            byteArray = BufferUtil.array(buffer);
            addressOffset = BufferUtil.ARRAY_BASE_OFFSET + buffer.arrayOffset() + offset;
            segment = MemorySegment.ofArray(byteArray);
        }
        capacity = length;
        wrapAdjustment = offset;
        isNative = buffer.isDirect();
    }

    @Override
    public void wrap(
        DirectBufferEx buffer)
    {
        segment = buffer.segment();
        byteArray = buffer.byteArray();
        byteBuffer = buffer.byteBuffer();
        addressOffset = buffer.addressOffset();
        capacity = buffer.capacity();
        wrapAdjustment = buffer.wrapAdjustment();
        isNative = buffer.byteArray() == null;
    }

    @Override
    public void wrap(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        segment = buffer.segment();
        byteArray = buffer.byteArray();
        byteBuffer = buffer.byteBuffer();
        addressOffset = buffer.addressOffset() + offset;
        capacity = length;
        wrapAdjustment = buffer.wrapAdjustment() + offset;
        isNative = buffer.byteArray() == null;
    }

    @Override
    public void wrap(
        DirectBuffer buffer)
    {
        if (buffer instanceof DirectBufferEx ex)
        {
            wrap(ex);
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
        if (buffer instanceof DirectBufferEx ex)
        {
            wrap(ex, offset, length);
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
        addressOffset = address;
        capacity = length;
        wrapAdjustment = 0;
        isNative = true;
    }

    @Override
    public void wrap(
        MemorySegment segment)
    {
        byteArray = null;
        byteBuffer = null;
        this.segment = segment;
        addressOffset = segment.address();
        capacity = (int) segment.byteSize();
        wrapAdjustment = 0;
        isNative = segment.isNative();
    }

    @Override
    public void wrap(
        MemorySegment segment,
        int offset,
        int length)
    {
        byteArray = null;
        byteBuffer = null;
        this.segment = segment;
        addressOffset = segment.address() + offset;
        capacity = length;
        wrapAdjustment = offset;
        isNative = segment.isNative();
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
    // DirectBufferEx accessors
    // -----------------------------------------------------------------------

    @Override
    public long addressOffset()
    {
        return addressOffset;
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
        Objects.checkFromIndexSize(index, length, capacity);
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
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return GLOBAL.get(LONG_LAYOUT, addressOffset + index);
        }
        return segment.get(LONG_LAYOUT, wrapAdjustment + index);
    }

    @Override
    public int getInt(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return GLOBAL.get(INT_LAYOUT, addressOffset + index);
        }
        return segment.get(INT_LAYOUT, wrapAdjustment + index);
    }

    @Override
    public short getShort(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            return GLOBAL.get(SHORT_LAYOUT, addressOffset + index);
        }
        return segment.get(SHORT_LAYOUT, wrapAdjustment + index);
    }

    @Override
    public byte getByte(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Byte.BYTES, capacity);
            }
            return GLOBAL.get(BYTE_LAYOUT, addressOffset + index);
        }
        return segment.get(BYTE_LAYOUT, wrapAdjustment + index);
    }

    @Override
    public double getDouble(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return Double.longBitsToDouble(GLOBAL.get(LONG_LAYOUT, addressOffset + index));
        }
        return Double.longBitsToDouble(segment.get(LONG_LAYOUT, wrapAdjustment + index));
    }

    @Override
    public float getFloat(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return Float.intBitsToFloat(GLOBAL.get(INT_LAYOUT, addressOffset + index));
        }
        return Float.intBitsToFloat(segment.get(INT_LAYOUT, wrapAdjustment + index));
    }

    @Override
    public char getChar(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            return (char) GLOBAL.get(SHORT_LAYOUT, addressOffset + index);
        }
        return (char) segment.get(SHORT_LAYOUT, wrapAdjustment + index);
    }

    // -----------------------------------------------------------------------
    // get — primitives (explicit byte order)
    // -----------------------------------------------------------------------

    @Override
    public long getLong(
        int index,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return GLOBAL.get(longLayout(byteOrder), addressOffset + index);
        }
        return segment.get(longLayout(byteOrder), wrapAdjustment + index);
    }

    @Override
    public int getInt(
        int index,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return GLOBAL.get(intLayout(byteOrder), addressOffset + index);
        }
        return segment.get(intLayout(byteOrder), wrapAdjustment + index);
    }

    @Override
    public short getShort(
        int index,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            return GLOBAL.get(shortLayout(byteOrder), addressOffset + index);
        }
        return segment.get(shortLayout(byteOrder), wrapAdjustment + index);
    }

    @Override
    public double getDouble(
        int index,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return Double.longBitsToDouble(GLOBAL.get(longLayout(byteOrder), addressOffset + index));
        }
        return Double.longBitsToDouble(segment.get(longLayout(byteOrder), wrapAdjustment + index));
    }

    @Override
    public float getFloat(
        int index,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return Float.intBitsToFloat(GLOBAL.get(intLayout(byteOrder), addressOffset + index));
        }
        return Float.intBitsToFloat(segment.get(intLayout(byteOrder), wrapAdjustment + index));
    }

    @Override
    public char getChar(
        int index,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            return (char) GLOBAL.get(shortLayout(byteOrder), addressOffset + index);
        }
        return (char) segment.get(shortLayout(byteOrder), wrapAdjustment + index);
    }

    // -----------------------------------------------------------------------
    // put — primitives (native byte order)
    // -----------------------------------------------------------------------

    @Override
    public void putLong(
        int index,
        long value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            GLOBAL.set(LONG_LAYOUT, addressOffset + index, value);
        }
        else
        {
            segment.set(LONG_LAYOUT, wrapAdjustment + index, value);
        }
    }

    @Override
    public void putInt(
        int index,
        int value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            GLOBAL.set(INT_LAYOUT, addressOffset + index, value);
        }
        else
        {
            segment.set(INT_LAYOUT, wrapAdjustment + index, value);
        }
    }

    @Override
    public void putShort(
        int index,
        short value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            GLOBAL.set(SHORT_LAYOUT, addressOffset + index, value);
        }
        else
        {
            segment.set(SHORT_LAYOUT, wrapAdjustment + index, value);
        }
    }

    @Override
    public void putByte(
        int index,
        byte value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Byte.BYTES, capacity);
            }
            GLOBAL.set(BYTE_LAYOUT, addressOffset + index, value);
        }
        else
        {
            segment.set(BYTE_LAYOUT, wrapAdjustment + index, value);
        }
    }

    @Override
    public void putDouble(
        int index,
        double value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            GLOBAL.set(LONG_LAYOUT, addressOffset + index, Double.doubleToRawLongBits(value));
        }
        else
        {
            segment.set(LONG_LAYOUT, wrapAdjustment + index, Double.doubleToRawLongBits(value));
        }
    }

    @Override
    public void putFloat(
        int index,
        float value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            GLOBAL.set(INT_LAYOUT, addressOffset + index, Float.floatToRawIntBits(value));
        }
        else
        {
            segment.set(INT_LAYOUT, wrapAdjustment + index, Float.floatToRawIntBits(value));
        }
    }

    @Override
    public void putChar(
        int index,
        char value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            GLOBAL.set(SHORT_LAYOUT, addressOffset + index, (short) value);
        }
        else
        {
            segment.set(SHORT_LAYOUT, wrapAdjustment + index, (short) value);
        }
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
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            GLOBAL.set(longLayout(byteOrder), addressOffset + index, value);
        }
        else
        {
            segment.set(longLayout(byteOrder), wrapAdjustment + index, value);
        }
    }

    @Override
    public void putInt(
        int index,
        int value,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            GLOBAL.set(intLayout(byteOrder), addressOffset + index, value);
        }
        else
        {
            segment.set(intLayout(byteOrder), wrapAdjustment + index, value);
        }
    }

    @Override
    public void putShort(
        int index,
        short value,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            GLOBAL.set(shortLayout(byteOrder), addressOffset + index, value);
        }
        else
        {
            segment.set(shortLayout(byteOrder), wrapAdjustment + index, value);
        }
    }

    @Override
    public void putDouble(
        int index,
        double value,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            GLOBAL.set(longLayout(byteOrder), addressOffset + index, Double.doubleToRawLongBits(value));
        }
        else
        {
            segment.set(longLayout(byteOrder), wrapAdjustment + index, Double.doubleToRawLongBits(value));
        }
    }

    @Override
    public void putFloat(
        int index,
        float value,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            GLOBAL.set(intLayout(byteOrder), addressOffset + index, Float.floatToRawIntBits(value));
        }
        else
        {
            segment.set(intLayout(byteOrder), wrapAdjustment + index, Float.floatToRawIntBits(value));
        }
    }

    @Override
    public void putChar(
        int index,
        char value,
        ByteOrder byteOrder)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            GLOBAL.set(shortLayout(byteOrder), addressOffset + index, (short) value);
        }
        else
        {
            segment.set(shortLayout(byteOrder), wrapAdjustment + index, (short) value);
        }
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
        MemorySegment.copy(segment, BYTE_LAYOUT, wrapAdjustment + index,
            MemorySegment.ofArray(dst), BYTE_LAYOUT, offset, length);
    }

    @Override
    public void getBytes(
        int index,
        MutableDirectBuffer dstBuffer,
        int dstIndex,
        int length)
    {
        if (dstBuffer instanceof UnsafeBufferEx safe)
        {
            MemorySegment.copy(segment, wrapAdjustment + index, safe.segment, safe.wrapAdjustment + dstIndex, length);
        }
        else
        {
            final byte[] dstArray = dstBuffer.byteArray();
            if (dstArray != null)
            {
                final int adjustment = dstBuffer.wrapAdjustment();
                MemorySegment.copy(segment, BYTE_LAYOUT, wrapAdjustment + index,
                    MemorySegment.ofArray(dstArray), BYTE_LAYOUT, adjustment + dstIndex, length);
            }
            else
            {
                final ByteBuffer dstBb = dstBuffer.byteBuffer();
                if (dstBb != null)
                {
                    final MemorySegment dst = dstBb.isDirect()
                        ? MemorySegment.ofAddress(BufferUtil.address(dstBb)).reinterpret(dstBb.capacity())
                        : MemorySegment.ofArray(BufferUtil.array(dstBb));
                    MemorySegment.copy(segment, wrapAdjustment + index,
                        dst, dstBuffer.wrapAdjustment() + dstIndex, length);
                }
                else
                {
                    for (int i = 0; i < length; i++)
                    {
                        dstBuffer.putByte(dstIndex + i, getByte(index + i));
                    }
                }
            }
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
        MemorySegment.copy(segment, wrapAdjustment + index, dst, dstOffset, length);
    }

    @Override
    public void getBytes(
        int index,
        MemorySegment dstSegment,
        int dstIndex,
        int length)
    {
        MemorySegment.copy(segment, wrapAdjustment + index, dstSegment, dstIndex, length);
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
            segment, BYTE_LAYOUT, wrapAdjustment + index, length);
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
        MemorySegment.copy(src, srcOffset, segment, wrapAdjustment + index, length);
    }

    @Override
    public void putBytes(
        int index,
        DirectBufferEx srcBuffer,
        int srcIndex,
        int length)
    {
        MemorySegment.copy(srcBuffer.segment(), JAVA_BYTE, srcBuffer.wrapAdjustment() + srcIndex,
            segment, JAVA_BYTE, wrapAdjustment + index, length);
    }

    @Override
    public void putBytes(
        int index,
        DirectBuffer srcBuffer,
        int srcIndex,
        int length)
    {
        if (srcBuffer instanceof DirectBufferEx safe)
        {
            MemorySegment.copy(safe.segment(), JAVA_BYTE, safe.wrapAdjustment() + srcIndex,
                segment, JAVA_BYTE, wrapAdjustment + index, length);
        }
        else
        {
            final byte[] srcArray = srcBuffer.byteArray();
            if (srcArray != null)
            {
                final int adjustment = srcBuffer.wrapAdjustment();
                MemorySegment.copy(
                    MemorySegment.ofArray(srcArray), BYTE_LAYOUT, adjustment + srcIndex,
                    segment, BYTE_LAYOUT, wrapAdjustment + index, length);
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
                        segment, wrapAdjustment + index, length);
                }
                else
                {
                    for (int i = 0; i < length; i++)
                    {
                        segment.set(BYTE_LAYOUT, wrapAdjustment + index + i, srcBuffer.getByte(srcIndex + i));
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
        MemorySegment.copy(srcSegment, srcIndex, segment, wrapAdjustment + index, length);
    }

    @Override
    public void setMemory(
        int index,
        int length,
        byte value)
    {
        segment.asSlice(wrapAdjustment + index, length).fill(value);
    }

    // -----------------------------------------------------------------------
    // Atomic — long volatile / ordered / opaque
    // -----------------------------------------------------------------------

    private static UnsupportedOperationException heapAtomicsUnsupported()
    {
        return new UnsupportedOperationException(
            "atomic access requires a native buffer (direct ByteBuffer, mapped file, or native MemorySegment)");
    }

    @Override
    public long getLongVolatile(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return (long) LONG_HANDLE.getVolatile(GLOBAL, addressOffset + index);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public void putLongVolatile(
        int index,
        long value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            LONG_HANDLE.setVolatile(GLOBAL, addressOffset + index, value);
        }
        else
        {
            throw heapAtomicsUnsupported();
        }
    }

    @Override
    public void putLongOrdered(
        int index,
        long value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            LONG_HANDLE.setRelease(GLOBAL, addressOffset + index, value);
        }
        else
        {
            throw heapAtomicsUnsupported();
        }
    }

    @Override
    public long addLongOrdered(
        int index,
        long increment)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            final long currentValue = (long) LONG_HANDLE.getAcquire(GLOBAL, addressOffset + index);
            LONG_HANDLE.setRelease(GLOBAL, addressOffset + index, currentValue + increment);
            return currentValue;
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public boolean compareAndSetLong(
        int index,
        long expectedValue,
        long updateValue)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return LONG_HANDLE.compareAndSet(GLOBAL, addressOffset + index, expectedValue, updateValue);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public long getAndSetLong(
        int index,
        long value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return (long) LONG_HANDLE.getAndSet(GLOBAL, addressOffset + index, value);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public long getAndAddLong(
        int index,
        long delta)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return (long) LONG_HANDLE.getAndAdd(GLOBAL, addressOffset + index, delta);
        }
        throw heapAtomicsUnsupported();
    }

    // -----------------------------------------------------------------------
    // Atomic — int volatile / ordered / opaque
    // -----------------------------------------------------------------------

    @Override
    public int getIntVolatile(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return (int) INT_HANDLE.getVolatile(GLOBAL, addressOffset + index);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public void putIntVolatile(
        int index,
        int value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            INT_HANDLE.setVolatile(GLOBAL, addressOffset + index, value);
        }
        else
        {
            throw heapAtomicsUnsupported();
        }
    }

    @Override
    public void putIntOrdered(
        int index,
        int value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            INT_HANDLE.setRelease(GLOBAL, addressOffset + index, value);
        }
        else
        {
            throw heapAtomicsUnsupported();
        }
    }

    @Override
    public int addIntOrdered(
        int index,
        int increment)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            final int currentValue = (int) INT_HANDLE.getAcquire(GLOBAL, addressOffset + index);
            INT_HANDLE.setRelease(GLOBAL, addressOffset + index, currentValue + increment);
            return currentValue;
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public boolean compareAndSetInt(
        int index,
        int expectedValue,
        int updateValue)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return INT_HANDLE.compareAndSet(GLOBAL, addressOffset + index, expectedValue, updateValue);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public int getAndSetInt(
        int index,
        int value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return (int) INT_HANDLE.getAndSet(GLOBAL, addressOffset + index, value);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public int getAndAddInt(
        int index,
        int delta)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return (int) INT_HANDLE.getAndAdd(GLOBAL, addressOffset + index, delta);
        }
        throw heapAtomicsUnsupported();
    }

    // -----------------------------------------------------------------------
    // Atomic — short / char / byte volatile
    // -----------------------------------------------------------------------

    @Override
    public short getShortVolatile(
        int index)
    {
        // No VarHandle for short on MemorySegment; use plain access
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            return GLOBAL.get(SHORT_LAYOUT, addressOffset + index);
        }
        return segment.get(SHORT_LAYOUT, wrapAdjustment + index);
    }

    @Override
    public void putShortVolatile(
        int index,
        short value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            GLOBAL.set(SHORT_LAYOUT, addressOffset + index, value);
        }
        else
        {
            segment.set(SHORT_LAYOUT, wrapAdjustment + index, value);
        }
    }

    @Override
    public char getCharVolatile(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            return (char) GLOBAL.get(SHORT_LAYOUT, addressOffset + index);
        }
        return (char) segment.get(SHORT_LAYOUT, wrapAdjustment + index);
    }

    @Override
    public void putCharVolatile(
        int index,
        char value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Short.BYTES, capacity);
            }
            GLOBAL.set(SHORT_LAYOUT, addressOffset + index, (short) value);
        }
        else
        {
            segment.set(SHORT_LAYOUT, wrapAdjustment + index, (short) value);
        }
    }

    @Override
    public byte getByteVolatile(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Byte.BYTES, capacity);
            }
            return GLOBAL.get(BYTE_LAYOUT, addressOffset + index);
        }
        return segment.get(BYTE_LAYOUT, wrapAdjustment + index);
    }

    @Override
    public void putByteVolatile(
        int index,
        byte value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Byte.BYTES, capacity);
            }
            GLOBAL.set(BYTE_LAYOUT, addressOffset + index, value);
        }
        else
        {
            segment.set(BYTE_LAYOUT, wrapAdjustment + index, value);
        }
    }

    // -----------------------------------------------------------------------
    // Atomic — acquire / release / opaque (Agrona 2.x additions)
    // -----------------------------------------------------------------------

    @Override
    public long getLongAcquire(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return (long) LONG_HANDLE.getAcquire(GLOBAL, addressOffset + index);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public void putLongRelease(
        int index,
        long value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            LONG_HANDLE.setRelease(GLOBAL, addressOffset + index, value);
        }
        else
        {
            throw heapAtomicsUnsupported();
        }
    }

    @Override
    public long getLongOpaque(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return (long) LONG_HANDLE.getOpaque(GLOBAL, addressOffset + index);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public void putLongOpaque(
        int index,
        long value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            LONG_HANDLE.setOpaque(GLOBAL, addressOffset + index, value);
        }
        else
        {
            throw heapAtomicsUnsupported();
        }
    }

    @Override
    public long addLongRelease(
        int index,
        long increment)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            final long currentValue = (long) LONG_HANDLE.getAcquire(GLOBAL, addressOffset + index);
            LONG_HANDLE.setRelease(GLOBAL, addressOffset + index, currentValue + increment);
            return currentValue;
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public long addLongOpaque(
        int index,
        long increment)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            final long currentValue = (long) LONG_HANDLE.getOpaque(GLOBAL, addressOffset + index);
            LONG_HANDLE.setOpaque(GLOBAL, addressOffset + index, currentValue + increment);
            return currentValue;
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public long compareAndExchangeLong(
        int index,
        long expectedValue,
        long updateValue)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Long.BYTES, capacity);
            }
            return (long) LONG_HANDLE.compareAndExchange(GLOBAL, addressOffset + index, expectedValue, updateValue);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public int getIntAcquire(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return (int) INT_HANDLE.getAcquire(GLOBAL, addressOffset + index);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public void putIntRelease(
        int index,
        int value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            INT_HANDLE.setRelease(GLOBAL, addressOffset + index, value);
        }
        else
        {
            throw heapAtomicsUnsupported();
        }
    }

    @Override
    public int getIntOpaque(
        int index)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return (int) INT_HANDLE.getOpaque(GLOBAL, addressOffset + index);
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public void putIntOpaque(
        int index,
        int value)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            INT_HANDLE.setOpaque(GLOBAL, addressOffset + index, value);
        }
        else
        {
            throw heapAtomicsUnsupported();
        }
    }

    @Override
    public int addIntRelease(
        int index,
        int increment)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            final int currentValue = (int) INT_HANDLE.getAcquire(GLOBAL, addressOffset + index);
            INT_HANDLE.setRelease(GLOBAL, addressOffset + index, currentValue + increment);
            return currentValue;
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public int addIntOpaque(
        int index,
        int increment)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            final int currentValue = (int) INT_HANDLE.getOpaque(GLOBAL, addressOffset + index);
            INT_HANDLE.setOpaque(GLOBAL, addressOffset + index, currentValue + increment);
            return currentValue;
        }
        throw heapAtomicsUnsupported();
    }

    @Override
    public int compareAndExchangeInt(
        int index,
        int expectedValue,
        int updateValue)
    {
        if (isNative)
        {
            if (SHOULD_BOUNDS_CHECK)
            {
                Objects.checkFromIndexSize(index, Integer.BYTES, capacity);
            }
            return (int) INT_HANDLE.compareAndExchange(GLOBAL, addressOffset + index, expectedValue, updateValue);
        }
        throw heapAtomicsUnsupported();
    }

    // -----------------------------------------------------------------------
    // String — ASCII (length-prefixed)
    // -----------------------------------------------------------------------

    @Override
    public String getStringAscii(
        int index)
    {
        final int length = getInt(index);
        return getStringWithoutLengthAscii(index + Integer.BYTES, length);
    }

    @Override
    public String getStringAscii(
        int index,
        ByteOrder byteOrder)
    {
        final int length = getInt(index, byteOrder);
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
        final int length = getInt(index);
        return getStringWithoutLengthAscii(index + Integer.BYTES, length, appendable);
    }

    @Override
    public int getStringAscii(
        int index,
        Appendable appendable,
        ByteOrder byteOrder)
    {
        final int length = getInt(index, byteOrder);
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
        MemorySegment.copy(segment, BYTE_LAYOUT, wrapAdjustment + index,
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
                appendable.append((char) getByte(index + i));
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
        final int length = getInt(index);
        return getStringWithoutLengthUtf8(index + Integer.BYTES, length);
    }

    @Override
    public String getStringUtf8(
        int index,
        ByteOrder byteOrder)
    {
        final int length = getInt(index, byteOrder);
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
        MemorySegment.copy(segment, BYTE_LAYOUT, wrapAdjustment + index,
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
        putInt(index, length);
        return Integer.BYTES + putStringWithoutLengthAscii(index + Integer.BYTES, value);
    }

    @Override
    public int putStringAscii(
        int index,
        CharSequence value)
    {
        final int length = value != null ? value.length() : 0;
        putInt(index, length);
        return Integer.BYTES + putStringWithoutLengthAscii(index + Integer.BYTES, value);
    }

    @Override
    public int putStringAscii(
        int index,
        String value,
        ByteOrder byteOrder)
    {
        final int length = value != null ? value.length() : 0;
        putInt(index, length, byteOrder);
        return Integer.BYTES + putStringWithoutLengthAscii(index + Integer.BYTES, value);
    }

    @Override
    public int putStringAscii(
        int index,
        CharSequence value,
        ByteOrder byteOrder)
    {
        final int length = value != null ? value.length() : 0;
        putInt(index, length, byteOrder);
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
            putByte(index + i, (byte) value.charAt(i));
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
            putByte(index + i, (byte) value.charAt(i));
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
            putByte(index + i, (byte) value.charAt(valueOffset + i));
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
            putByte(index + i, (byte) value.charAt(valueOffset + i));
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
        segment.set(intLayout(byteOrder), wrapAdjustment + index, length);
        MemorySegment.copy(MemorySegment.ofArray(bytes), BYTE_LAYOUT, 0,
            segment, BYTE_LAYOUT, wrapAdjustment + index + Integer.BYTES, length);
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
            final byte b = getByte(index + i);
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
            final byte b = getByte(index + i);
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
        final boolean negative = getByte(index) == '-';
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
        final boolean negative = getByte(index) == '-';
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
            putByte(index + i, (byte) '0');
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
            putByte(index, (byte) ('0' + digit));
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
        return "UnsafeBufferEx{capacity=" + capacity + "}";
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
