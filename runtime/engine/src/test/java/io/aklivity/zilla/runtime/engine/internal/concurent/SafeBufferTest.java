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
package io.aklivity.zilla.runtime.engine.internal.concurent;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.concurent.SafeBuffer;
public class SafeBufferTest
{
    // -----------------------------------------------------------------------
    // Construction and wrap
    // -----------------------------------------------------------------------

    @Test
    public void shouldWrapByteArray()
    {
        final byte[] array = new byte[64];
        final SafeBuffer buffer = new SafeBuffer(array);

        assertEquals(64, buffer.capacity());
        assertNotNull(buffer.segment());
        assertEquals(array, buffer.byteArray());
        assertNull(buffer.byteBuffer());
        assertEquals(0, buffer.wrapAdjustment());
        assertFalse(buffer.isExpandable());
    }

    @Test
    public void shouldWrapByteArrayWithOffset()
    {
        final byte[] array = new byte[64];
        final SafeBuffer buffer = new SafeBuffer(array, 8, 32);

        assertEquals(32, buffer.capacity());
        assertEquals(array, buffer.byteArray());
        assertEquals(8, buffer.wrapAdjustment());
    }

    @Test
    public void shouldWrapHeapByteBuffer()
    {
        final ByteBuffer bb = ByteBuffer.allocate(64);
        final SafeBuffer buffer = new SafeBuffer(bb);

        assertEquals(64, buffer.capacity());
        assertNotNull(buffer.byteArray());
        assertEquals(bb, buffer.byteBuffer());
    }

    @Test
    public void shouldWrapDirectByteBuffer()
    {
        final ByteBuffer bb = ByteBuffer.allocateDirect(64);
        final SafeBuffer buffer = new SafeBuffer(bb);

        assertEquals(64, buffer.capacity());
        assertNull(buffer.byteArray());
        assertEquals(bb, buffer.byteBuffer());
    }

    @Test
    public void shouldWrapDirectByteBufferWithOffset()
    {
        final ByteBuffer bb = ByteBuffer.allocateDirect(64);
        final SafeBuffer buffer = new SafeBuffer(bb, 16, 32);

        assertEquals(32, buffer.capacity());
        assertNull(buffer.byteArray());
    }

    @Test
    public void shouldWrapAnotherSafeBuffer()
    {
        final SafeBuffer original = new SafeBuffer(new byte[64]);
        original.putInt(0, 42);

        final SafeBuffer copy = new SafeBuffer(original);
        assertEquals(42, copy.getInt(0));
        assertEquals(64, copy.capacity());
    }

    @Test
    public void shouldWrapAnotherSafeBufferWithOffset()
    {
        final SafeBuffer original = new SafeBuffer(new byte[64]);
        original.putInt(8, 99);

        final SafeBuffer slice = new SafeBuffer(original, 8, 32);
        assertEquals(99, slice.getInt(0));
        assertEquals(32, slice.capacity());
    }

    @Test
    public void shouldWrapUnsafeBufferBackedByArray()
    {
        final UnsafeBuffer unsafeBuffer = new SafeBuffer(new byte[64]);
        unsafeBuffer.putInt(4, 123);

        final SafeBuffer buffer = new SafeBuffer(unsafeBuffer);
        assertEquals(123, buffer.getInt(4));
        assertEquals(64, buffer.capacity());
    }

    @Test
    public void shouldWrapUnsafeBufferBackedByDirectByteBuffer()
    {
        final UnsafeBuffer unsafeBuffer = new SafeBuffer(ByteBuffer.allocateDirect(64));
        unsafeBuffer.putInt(4, 456);

        final SafeBuffer buffer = new SafeBuffer(unsafeBuffer);
        assertEquals(456, buffer.getInt(4));
    }

    @Test
    public void shouldWrapDefaultConstructor()
    {
        final SafeBuffer buffer = new SafeBuffer();
        assertEquals(0, buffer.capacity());
    }

    @Test
    public void shouldReWrap()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[32]);
        assertEquals(32, buffer.capacity());

        buffer.wrap(new byte[64]);
        assertEquals(64, buffer.capacity());
    }

    @Test
    public void shouldExposeSegment()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[16]);
        final MemorySegment seg = buffer.segment();

        assertNotNull(seg);
        assertEquals(16, seg.byteSize());
    }

    @Test
    public void shouldWrapMemorySegment()
    {
        final MemorySegment seg = MemorySegment.ofArray(new byte[64]);
        final SafeBuffer buffer = new SafeBuffer(seg);

        assertEquals(64, buffer.capacity());
        assertNotNull(buffer.segment());
        assertEquals(0, buffer.wrapAdjustment());
    }

    @Test
    public void shouldWrapMemorySegmentWithOffset()
    {
        final MemorySegment seg = MemorySegment.ofArray(new byte[64]);
        final SafeBuffer buffer = new SafeBuffer(seg, 8, 32);

        assertEquals(32, buffer.capacity());
        assertEquals(8, buffer.wrapAdjustment());
    }

    @Test
    public void shouldWrapMemorySegmentAndReadWrite()
    {
        final MemorySegment seg = MemorySegment.ofArray(new byte[64]);
        final SafeBuffer buffer = new SafeBuffer(seg);

        buffer.putInt(0, 42);
        assertEquals(42, buffer.getInt(0));
    }

    // -----------------------------------------------------------------------
    // get/put — int (native order)
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutInt()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putInt(0, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, buffer.getInt(0));

        buffer.putInt(4, Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, buffer.getInt(4));

        buffer.putInt(8, 0);
        assertEquals(0, buffer.getInt(8));
    }

    // -----------------------------------------------------------------------
    // get/put — long (native order)
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutLong()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putLong(0, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, buffer.getLong(0));

        buffer.putLong(8, Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, buffer.getLong(8));
    }

    // -----------------------------------------------------------------------
    // get/put — short (native order)
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutShort()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putShort(0, Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, buffer.getShort(0));

        buffer.putShort(2, Short.MIN_VALUE);
        assertEquals(Short.MIN_VALUE, buffer.getShort(2));
    }

    // -----------------------------------------------------------------------
    // get/put — byte
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutByte()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putByte(0, (byte) 0x7F);
        assertEquals((byte) 0x7F, buffer.getByte(0));

        buffer.putByte(1, (byte) 0x80);
        assertEquals((byte) 0x80, buffer.getByte(1));
    }

    // -----------------------------------------------------------------------
    // get/put — double
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutDouble()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putDouble(0, 3.14159);
        assertEquals(3.14159, buffer.getDouble(0), 0.00001);

        buffer.putDouble(8, Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, buffer.getDouble(8), 0.0);
    }

    // -----------------------------------------------------------------------
    // get/put — float
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutFloat()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putFloat(0, 2.71828f);
        assertEquals(2.71828f, buffer.getFloat(0), 0.00001f);
    }

    // -----------------------------------------------------------------------
    // get/put — char
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutChar()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putChar(0, 'Z');
        assertEquals('Z', buffer.getChar(0));
    }

    // -----------------------------------------------------------------------
    // get/put — explicit byte order
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutIntBigEndian()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putInt(0, 0x01020304, BIG_ENDIAN);
        assertEquals(0x01020304, buffer.getInt(0, BIG_ENDIAN));

        // Verify byte-level encoding
        assertEquals((byte) 0x01, buffer.getByte(0));
        assertEquals((byte) 0x02, buffer.getByte(1));
        assertEquals((byte) 0x03, buffer.getByte(2));
        assertEquals((byte) 0x04, buffer.getByte(3));
    }

    @Test
    public void shouldGetAndPutLongBigEndian()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putLong(0, 0x0102030405060708L, BIG_ENDIAN);
        assertEquals(0x0102030405060708L, buffer.getLong(0, BIG_ENDIAN));
    }

    @Test
    public void shouldGetAndPutShortBigEndian()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putShort(0, (short) 0x0102, BIG_ENDIAN);
        assertEquals((short) 0x0102, buffer.getShort(0, BIG_ENDIAN));
    }

    @Test
    public void shouldGetAndPutDoubleBigEndian()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putDouble(0, 1.5, BIG_ENDIAN);
        assertEquals(1.5, buffer.getDouble(0, BIG_ENDIAN), 0.0);
    }

    @Test
    public void shouldGetAndPutFloatBigEndian()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putFloat(0, 1.5f, BIG_ENDIAN);
        assertEquals(1.5f, buffer.getFloat(0, BIG_ENDIAN), 0.0f);
    }

    @Test
    public void shouldGetAndPutCharBigEndian()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);

        buffer.putChar(0, 'A', BIG_ENDIAN);
        assertEquals('A', buffer.getChar(0, BIG_ENDIAN));
    }

    // -----------------------------------------------------------------------
    // getBytes / putBytes
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutByteArray()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        final byte[] src = {1, 2, 3, 4, 5};

        buffer.putBytes(0, src);

        final byte[] dst = new byte[5];
        buffer.getBytes(0, dst);
        assertArrayEquals(src, dst);
    }

    @Test
    public void shouldGetAndPutByteArrayWithOffset()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        final byte[] src = {0, 0, 1, 2, 3};

        buffer.putBytes(10, src, 2, 3);

        final byte[] dst = new byte[5];
        buffer.getBytes(10, dst, 1, 3);
        assertEquals(1, dst[1]);
        assertEquals(2, dst[2]);
        assertEquals(3, dst[3]);
    }

    @Test
    public void shouldPutBytesFromDirectBuffer()
    {
        final SafeBuffer src = new SafeBuffer(new byte[64]);
        src.putInt(0, 0xDEADBEEF);

        final SafeBuffer dst = new SafeBuffer(new byte[64]);
        dst.putBytes(8, src, 0, 4);

        assertEquals(0xDEADBEEF, dst.getInt(8));
    }

    @Test
    public void shouldPutBytesFromSafeBuffer()
    {
        final UnsafeBuffer src = new SafeBuffer(new byte[64]);
        src.putInt(0, 0xCAFEBABE);

        final SafeBuffer dst = new SafeBuffer(new byte[64]);
        dst.putBytes(0, src, 0, 4);

        assertEquals(0xCAFEBABE, dst.getInt(0));
    }

    @Test
    public void shouldGetBytesToByteBuffer()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putBytes(0, new byte[]{10, 20, 30, 40});

        final ByteBuffer dst = ByteBuffer.allocate(8);
        buffer.getBytes(0, dst, 0, 4);

        assertEquals(10, dst.get(0));
        assertEquals(20, dst.get(1));
        assertEquals(30, dst.get(2));
        assertEquals(40, dst.get(3));
    }

    @Test
    public void shouldPutBytesFromByteBuffer()
    {
        final ByteBuffer src = ByteBuffer.allocate(8);
        src.put(new byte[]{10, 20, 30, 40});
        src.flip();

        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putBytes(0, src, 0, 4);

        assertEquals(10, buffer.getByte(0));
        assertEquals(20, buffer.getByte(1));
    }

    @Test
    public void shouldGetBytesToMutableDirectBuffer()
    {
        final SafeBuffer src = new SafeBuffer(new byte[64]);
        src.putInt(0, 42);

        final SafeBuffer dst = new SafeBuffer(new byte[64]);
        src.getBytes(0, dst, 0, 4);

        assertEquals(42, dst.getInt(0));
    }

    @Test
    public void shouldGetBytesToMemorySegment()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putInt(0, 0xDEADBEEF);

        final MemorySegment dst = MemorySegment.ofArray(new byte[64]);
        buffer.getBytes(0, dst, 0, 4);

        assertEquals(0xDEADBEEF, dst.get(JAVA_INT_UNALIGNED, 0));
    }

    @Test
    public void shouldGetBytesToMemorySegmentWithOffset()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putInt(4, 42);

        final MemorySegment dst = MemorySegment.ofArray(new byte[64]);
        buffer.getBytes(4, dst, 8, 4);

        assertEquals(42, dst.get(JAVA_INT_UNALIGNED, 8));
    }

    @Test
    public void shouldPutBytesFromMemorySegment()
    {
        final MemorySegment src = MemorySegment.ofArray(new byte[64]);
        src.set(JAVA_INT_UNALIGNED, 0, 0xCAFEBABE);

        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putBytes(0, src, 0, 4);

        assertEquals(0xCAFEBABE, buffer.getInt(0));
    }

    @Test
    public void shouldPutBytesFromMemorySegmentWithOffset()
    {
        final MemorySegment src = MemorySegment.ofArray(new byte[64]);
        src.set(JAVA_INT_UNALIGNED, 8, 99);

        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putBytes(4, src, 8, 4);

        assertEquals(99, buffer.getInt(4));
    }

    // -----------------------------------------------------------------------
    // setMemory
    // -----------------------------------------------------------------------

    @Test
    public void shouldSetMemory()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.setMemory(0, 16, (byte) 0xFF);

        for (int i = 0; i < 16; i++)
        {
            assertEquals((byte) 0xFF, buffer.getByte(i));
        }
        assertEquals(0, buffer.getByte(16));
    }

    // -----------------------------------------------------------------------
    // Atomic — int volatile
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutIntVolatile()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntVolatile(0, 42);
        assertEquals(42, buffer.getIntVolatile(0));
    }

    @Test
    public void shouldPutIntOrdered()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntOrdered(0, 99);
        assertEquals(99, buffer.getIntVolatile(0));
    }

    @Test
    public void shouldCompareAndSetInt()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntVolatile(0, 10);
        assertTrue(buffer.compareAndSetInt(0, 10, 20));
        assertEquals(20, buffer.getIntVolatile(0));

        assertFalse(buffer.compareAndSetInt(0, 10, 30));
        assertEquals(20, buffer.getIntVolatile(0));
    }

    @Test
    public void shouldGetAndAddInt()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntVolatile(0, 100);
        final int previous = buffer.getAndAddInt(0, 50);

        assertEquals(100, previous);
        assertEquals(150, buffer.getIntVolatile(0));
    }

    @Test
    public void shouldGetAndSetInt()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntVolatile(0, 100);
        final int previous = buffer.getAndSetInt(0, 200);

        assertEquals(100, previous);
        assertEquals(200, buffer.getIntVolatile(0));
    }

    @Test
    public void shouldAddIntOrdered()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntVolatile(0, 10);
        final int previous = buffer.addIntOrdered(0, 5);

        assertEquals(10, previous);
        assertEquals(15, buffer.getIntVolatile(0));
    }

    // -----------------------------------------------------------------------
    // Atomic — long volatile
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutLongVolatile()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongVolatile(0, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, buffer.getLongVolatile(0));
    }

    @Test
    public void shouldPutLongOrdered()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongOrdered(0, 123456789L);
        assertEquals(123456789L, buffer.getLongVolatile(0));
    }

    @Test
    public void shouldCompareAndSetLong()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongVolatile(0, 10L);
        assertTrue(buffer.compareAndSetLong(0, 10L, 20L));
        assertEquals(20L, buffer.getLongVolatile(0));

        assertFalse(buffer.compareAndSetLong(0, 10L, 30L));
        assertEquals(20L, buffer.getLongVolatile(0));
    }

    @Test
    public void shouldGetAndAddLong()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongVolatile(0, 1000L);
        final long previous = buffer.getAndAddLong(0, 500L);

        assertEquals(1000L, previous);
        assertEquals(1500L, buffer.getLongVolatile(0));
    }

    @Test
    public void shouldGetAndSetLong()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongVolatile(0, 1000L);
        final long previous = buffer.getAndSetLong(0, 2000L);

        assertEquals(1000L, previous);
        assertEquals(2000L, buffer.getLongVolatile(0));
    }

    @Test
    public void shouldAddLongOrdered()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongVolatile(0, 100L);
        final long previous = buffer.addLongOrdered(0, 50L);

        assertEquals(100L, previous);
        assertEquals(150L, buffer.getLongVolatile(0));
    }

    // -----------------------------------------------------------------------
    // Atomic — acquire / release / opaque
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutLongAcquireRelease()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongRelease(0, 42L);
        assertEquals(42L, buffer.getLongAcquire(0));
    }

    @Test
    public void shouldGetAndPutLongOpaque()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongOpaque(0, 77L);
        assertEquals(77L, buffer.getLongOpaque(0));
    }

    @Test
    public void shouldGetAndPutIntAcquireRelease()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntRelease(0, 42);
        assertEquals(42, buffer.getIntAcquire(0));
    }

    @Test
    public void shouldGetAndPutIntOpaque()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntOpaque(0, 77);
        assertEquals(77, buffer.getIntOpaque(0));
    }

    @Test
    public void shouldCompareAndExchangeInt()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntVolatile(0, 10);
        final int witness = buffer.compareAndExchangeInt(0, 10, 20);

        assertEquals(10, witness);
        assertEquals(20, buffer.getIntVolatile(0));
    }

    @Test
    public void shouldCompareAndExchangeLong()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongVolatile(0, 100L);
        final long witness = buffer.compareAndExchangeLong(0, 100L, 200L);

        assertEquals(100L, witness);
        assertEquals(200L, buffer.getLongVolatile(0));
    }

    @Test
    public void shouldAddIntRelease()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putIntVolatile(0, 10);
        final int previous = buffer.addIntRelease(0, 5);

        assertEquals(10, previous);
        assertEquals(15, buffer.getIntVolatile(0));
    }

    @Test
    public void shouldAddLongRelease()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putLongVolatile(0, 10L);
        final long previous = buffer.addLongRelease(0, 5L);

        assertEquals(10L, previous);
        assertEquals(15L, buffer.getLongVolatile(0));
    }

    // -----------------------------------------------------------------------
    // Atomic — short / char / byte volatile
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutShortVolatile()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putShortVolatile(0, (short) 1234);
        assertEquals((short) 1234, buffer.getShortVolatile(0));
    }

    @Test
    public void shouldGetAndPutCharVolatile()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putCharVolatile(0, 'X');
        assertEquals('X', buffer.getCharVolatile(0));
    }

    @Test
    public void shouldGetAndPutByteVolatile()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));

        buffer.putByteVolatile(0, (byte) 42);
        assertEquals((byte) 42, buffer.getByteVolatile(0));
    }

    // -----------------------------------------------------------------------
    // Bounds checking
    // -----------------------------------------------------------------------

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldThrowOnGetIntOutOfBounds()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[8]);
        buffer.getInt(8);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldThrowOnPutIntOutOfBounds()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[8]);
        buffer.putInt(8, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldThrowOnGetLongOutOfBounds()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[4]);
        buffer.getLong(0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldThrowOnNegativeIndex()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.getInt(-1);
    }

    @Test
    public void shouldCheckLimit()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[16]);
        buffer.checkLimit(16);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldThrowOnCheckLimitExceeded()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[16]);
        buffer.checkLimit(17);
    }

    @Test
    public void shouldBoundsCheck()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[16]);
        buffer.boundsCheck(0, 16);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldThrowOnBoundsCheckExceeded()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[16]);
        buffer.boundsCheck(0, 17);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldThrowOnBoundsCheckNegativeIndex()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[16]);
        buffer.boundsCheck(-1, 4);
    }

    // -----------------------------------------------------------------------
    // String operations
    // -----------------------------------------------------------------------

    @Test
    public void shouldGetAndPutStringAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[128]);

        final int written = buffer.putStringAscii(0, "Hello");
        assertEquals(4 + 5, written);
        assertEquals("Hello", buffer.getStringAscii(0));
    }

    @Test
    public void shouldGetAndPutStringAsciiWithByteOrder()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[128]);

        buffer.putStringAscii(0, "World", BIG_ENDIAN);
        assertEquals("World", buffer.getStringAscii(0, BIG_ENDIAN));
    }

    @Test
    public void shouldGetAndPutStringWithoutLengthAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[128]);

        final int written = buffer.putStringWithoutLengthAscii(0, "ABC");
        assertEquals(3, written);
        assertEquals("ABC", buffer.getStringWithoutLengthAscii(0, 3));
    }

    @Test
    public void shouldGetAndPutStringUtf8()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[128]);

        buffer.putStringUtf8(0, "Hello");
        assertEquals("Hello", buffer.getStringUtf8(0));
    }

    @Test
    public void shouldPutStringWithoutLengthAsciiWithOffset()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[128]);

        final int written = buffer.putStringWithoutLengthAscii(0, "ABCDEF", 2, 3);
        assertEquals(3, written);
        assertEquals("CDE", buffer.getStringWithoutLengthAscii(0, 3));
    }

    @Test
    public void shouldAppendStringAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[128]);
        buffer.putStringAscii(0, "test");

        final StringBuilder sb = new StringBuilder();
        final int length = buffer.getStringAscii(0, sb);
        assertEquals(4, length);
        assertEquals("test", sb.toString());
    }

    @Test
    public void shouldPutNullStringAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[128]);
        final int written = buffer.putStringWithoutLengthAscii(0, (String) null);
        assertEquals(0, written);
    }

    // -----------------------------------------------------------------------
    // ASCII int/long parsing
    // -----------------------------------------------------------------------

    @Test
    public void shouldParseNaturalIntAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putStringWithoutLengthAscii(0, "12345");
        assertEquals(12345, buffer.parseNaturalIntAscii(0, 5));
    }

    @Test
    public void shouldParseIntAsciiNegative()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putStringWithoutLengthAscii(0, "-42");
        assertEquals(-42, buffer.parseIntAscii(0, 3));
    }

    @Test
    public void shouldParseLongAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putStringWithoutLengthAscii(0, "9876543210");
        assertEquals(9876543210L, buffer.parseLongAscii(0, 10));
    }

    @Test
    public void shouldPutIntAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        final int written = buffer.putIntAscii(0, -99);
        assertEquals(3, written);
        assertEquals("-99", buffer.getStringWithoutLengthAscii(0, 3));
    }

    @Test
    public void shouldPutNaturalIntAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        final int written = buffer.putNaturalIntAscii(0, 42);
        assertEquals(2, written);
        assertEquals("42", buffer.getStringWithoutLengthAscii(0, 2));
    }

    @Test
    public void shouldPutNaturalPaddedIntAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        buffer.putNaturalPaddedIntAscii(0, 5, 42);
        assertEquals("00042", buffer.getStringWithoutLengthAscii(0, 5));
    }

    @Test
    public void shouldPutNaturalLongAscii()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[64]);
        final int written = buffer.putNaturalLongAscii(0, 123456789L);
        assertEquals(9, written);
    }

    // -----------------------------------------------------------------------
    // Interop with UnsafeBuffer
    // -----------------------------------------------------------------------

    @Test
    public void shouldBeByteCompatibleWithSafeBuffer()
    {
        final byte[] backing = new byte[64];
        final UnsafeBuffer unsafeBuffer = new SafeBuffer(backing);
        final SafeBuffer safeBuffer = new SafeBuffer(backing);

        unsafeBuffer.putInt(0, 0xDEADC0DE);
        assertEquals(0xDEADC0DE, safeBuffer.getInt(0));

        safeBuffer.putLong(8, 0x1234567890ABCDEFL);
        assertEquals(0x1234567890ABCDEFL, unsafeBuffer.getLong(8));
    }

    @Test
    public void shouldCopyBetweenSafeAndUnsafe()
    {
        final SafeBuffer safe = new SafeBuffer(new byte[64]);
        safe.putInt(0, 111);
        safe.putInt(4, 222);

        final UnsafeBuffer unsafe = new SafeBuffer(new byte[64]);
        safe.getBytes(0, unsafe, 0, 8);

        assertEquals(111, unsafe.getInt(0));
        assertEquals(222, unsafe.getInt(4));
    }

    // -----------------------------------------------------------------------
    // Comparable / equals / hashCode
    // -----------------------------------------------------------------------

    @Test
    public void shouldCompareBuffers()
    {
        final SafeBuffer a = new SafeBuffer(new byte[]{1, 2, 3});
        final SafeBuffer b = new SafeBuffer(new byte[]{1, 2, 4});
        final SafeBuffer c = new SafeBuffer(new byte[]{1, 2, 3});

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(c));
    }

    @Test
    public void shouldCompareBuffersOfDifferentLength()
    {
        final SafeBuffer shorter = new SafeBuffer(new byte[]{1, 2});
        final SafeBuffer longer = new SafeBuffer(new byte[]{1, 2, 3});

        assertTrue(shorter.compareTo(longer) < 0);
        assertTrue(longer.compareTo(shorter) > 0);
    }

    @Test
    public void shouldBeEqualForSameContent()
    {
        final SafeBuffer a = new SafeBuffer(new byte[]{1, 2, 3, 4});
        final SafeBuffer b = new SafeBuffer(new byte[]{1, 2, 3, 4});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void shouldNotBeEqualForDifferentContent()
    {
        final SafeBuffer a = new SafeBuffer(new byte[]{1, 2, 3});
        final SafeBuffer b = new SafeBuffer(new byte[]{1, 2, 4});

        assertFalse(a.equals(b));
    }

    @Test
    public void shouldNotBeEqualForDifferentCapacity()
    {
        final SafeBuffer a = new SafeBuffer(new byte[]{1, 2});
        final SafeBuffer b = new SafeBuffer(new byte[]{1, 2, 3});

        assertFalse(a.equals(b));
    }

    // -----------------------------------------------------------------------
    // toString
    // -----------------------------------------------------------------------

    @Test
    public void shouldToString()
    {
        final SafeBuffer buffer = new SafeBuffer(new byte[32]);
        assertEquals("SafeBuffer{capacity=32}", buffer.toString());
    }

    // -----------------------------------------------------------------------
    // verifyAlignment
    // -----------------------------------------------------------------------

    @Test
    public void shouldVerifyAlignmentOnAlignedBuffer()
    {
        final SafeBuffer buffer = new SafeBuffer(ByteBuffer.allocateDirect(64));
        buffer.verifyAlignment();
    }
}
