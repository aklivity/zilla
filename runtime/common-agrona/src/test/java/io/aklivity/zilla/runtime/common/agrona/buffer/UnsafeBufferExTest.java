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

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Exercises {@link UnsafeBufferEx}, which branches per access: heap-backed
 * instances take the bounded segment path, native (off-heap) instances take the
 * unchecked {@code GLOBAL} segment path.
 * <p>
 * {@link RoundTrips} runs every read/write round-trip across all backing kinds
 * (heap array, heap array with offset, heap and direct {@link ByteBuffer},
 * direct with offset, heap {@link MemorySegment} — the {@code isNative == false}
 * edge — and a native {@link Arena} segment) so both branches are covered.
 * {@link Modes} holds the path-specific tests: atomics on native (aligned)
 * backings only — heap {@code byte[]} segments cannot satisfy aligned
 * {@code VarHandle} access — and implicit bounds checks on heap only, since the
 * native path is intentionally unchecked.
 */
@RunWith(Enclosed.class)
public class UnsafeBufferExTest
{
    @RunWith(Parameterized.class)
    public static class RoundTrips
    {
        @FunctionalInterface
        public interface BufferFactory
        {
            UnsafeBufferEx create();
        }

        @Parameters(name = "{0}")
        public static Collection<Object[]> backings()
        {
            return List.of(new Object[][]
            {
                {"heapArray", (BufferFactory) () -> new UnsafeBufferEx(new byte[256])},
                {"heapArrayOffset", (BufferFactory) () -> new UnsafeBufferEx(new byte[512], 64, 256)},
                {"heapByteBuffer", (BufferFactory) () -> new UnsafeBufferEx(ByteBuffer.allocate(256))},
                {"directByteBuffer", (BufferFactory) () -> new UnsafeBufferEx(ByteBuffer.allocateDirect(256))},
                {"directByteBufferOffset", (BufferFactory) () -> new UnsafeBufferEx(ByteBuffer.allocateDirect(512), 64, 256)},
                {"heapSegment", (BufferFactory) () -> new UnsafeBufferEx(MemorySegment.ofArray(new byte[256]))},
                {"nativeSegment", (BufferFactory) () -> new UnsafeBufferEx(Arena.ofAuto().allocate(256))},
            });
        }

        @Parameter(0)
        public String name;

        @Parameter(1)
        public BufferFactory factory;

        @Test
        public void shouldRoundTripIntegralPrimitives()
        {
            final UnsafeBufferEx buffer = factory.create();

            buffer.putLong(0, Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, buffer.getLong(0));
            buffer.putLong(8, Long.MIN_VALUE);
            assertEquals(Long.MIN_VALUE, buffer.getLong(8));

            buffer.putInt(16, Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, buffer.getInt(16));
            buffer.putInt(20, Integer.MIN_VALUE);
            assertEquals(Integer.MIN_VALUE, buffer.getInt(20));

            buffer.putShort(24, Short.MAX_VALUE);
            assertEquals(Short.MAX_VALUE, buffer.getShort(24));

            buffer.putByte(28, (byte) 0x80);
            assertEquals((byte) 0x80, buffer.getByte(28));

            buffer.putChar(30, 'Z');
            assertEquals('Z', buffer.getChar(30));
        }

        @Test
        public void shouldRoundTripFloatingPrimitives()
        {
            final UnsafeBufferEx buffer = factory.create();

            buffer.putDouble(0, 3.14159);
            assertEquals(3.14159, buffer.getDouble(0), 0.00001);

            buffer.putFloat(8, 2.71828f);
            assertEquals(2.71828f, buffer.getFloat(8), 0.00001f);
        }

        @Test
        public void shouldRoundTripBigEndian()
        {
            final UnsafeBufferEx buffer = factory.create();

            buffer.putInt(0, 0x01020304, BIG_ENDIAN);
            assertEquals(0x01020304, buffer.getInt(0, BIG_ENDIAN));
            assertEquals((byte) 0x01, buffer.getByte(0));
            assertEquals((byte) 0x04, buffer.getByte(3));

            buffer.putLong(8, 0x0102030405060708L, BIG_ENDIAN);
            assertEquals(0x0102030405060708L, buffer.getLong(8, BIG_ENDIAN));

            buffer.putShort(16, (short) 0x0102, BIG_ENDIAN);
            assertEquals((short) 0x0102, buffer.getShort(16, BIG_ENDIAN));

            buffer.putChar(18, 'A', BIG_ENDIAN);
            assertEquals('A', buffer.getChar(18, BIG_ENDIAN));
        }

        @Test
        public void shouldRoundTripByteArray()
        {
            final UnsafeBufferEx buffer = factory.create();

            final byte[] src = {1, 2, 3, 4, 5};
            buffer.putBytes(0, src);
            final byte[] dst = new byte[5];
            buffer.getBytes(0, dst);
            assertArrayEquals(src, dst);

            final byte[] src2 = {0, 0, 1, 2, 3};
            buffer.putBytes(10, src2, 2, 3);
            final byte[] dst2 = new byte[3];
            buffer.getBytes(10, dst2, 0, 3);
            assertEquals(1, dst2[0]);
            assertEquals(3, dst2[2]);
        }

        @Test
        public void shouldRoundTripMemorySegment()
        {
            final UnsafeBufferEx buffer = factory.create();
            buffer.putInt(0, 0xDEADBEEF);

            final MemorySegment dst = MemorySegment.ofArray(new byte[16]);
            buffer.getBytes(0, dst, 0, 4);
            assertEquals(0xDEADBEEF, dst.get(JAVA_INT_UNALIGNED, 0));

            final MemorySegment src = MemorySegment.ofArray(new byte[16]);
            src.set(JAVA_INT_UNALIGNED, 0, 0xCAFEBABE);
            buffer.putBytes(8, src, 0, 4);
            assertEquals(0xCAFEBABE, buffer.getInt(8));
        }

        @Test
        public void shouldSetMemory()
        {
            final UnsafeBufferEx buffer = factory.create();
            buffer.setMemory(0, 16, (byte) 0xFF);

            for (int i = 0; i < 16; i++)
            {
                assertEquals((byte) 0xFF, buffer.getByte(i));
            }
            assertEquals(0, buffer.getByte(16));
        }

        @Test
        public void shouldRoundTripStrings()
        {
            final UnsafeBufferEx buffer = factory.create();

            final int written = buffer.putStringAscii(0, "Hello");
            assertEquals(4 + 5, written);
            assertEquals("Hello", buffer.getStringAscii(0));

            buffer.putStringAscii(16, "World", BIG_ENDIAN);
            assertEquals("World", buffer.getStringAscii(16, BIG_ENDIAN));

            buffer.putStringWithoutLengthAscii(32, "ABC");
            assertEquals("ABC", buffer.getStringWithoutLengthAscii(32, 3));

            buffer.putStringUtf8(40, "Hello");
            assertEquals("Hello", buffer.getStringUtf8(40));

            assertEquals(0, buffer.putStringWithoutLengthAscii(80, (String) null));
        }

        @Test
        public void shouldParseAndEncodeAscii()
        {
            final UnsafeBufferEx buffer = factory.create();

            buffer.putStringWithoutLengthAscii(0, "12345");
            assertEquals(12345, buffer.parseNaturalIntAscii(0, 5));

            buffer.putStringWithoutLengthAscii(8, "-42");
            assertEquals(-42, buffer.parseIntAscii(8, 3));

            buffer.putStringWithoutLengthAscii(16, "9876543210");
            assertEquals(9876543210L, buffer.parseLongAscii(16, 10));

            buffer.putNaturalPaddedIntAscii(32, 5, 42);
            assertEquals("00042", buffer.getStringWithoutLengthAscii(32, 5));
        }

        @Test
        public void shouldExposeSegmentAndCapacity()
        {
            final UnsafeBufferEx buffer = factory.create();

            assertNotNull(buffer.segment());
            assertEquals(256, buffer.capacity());
        }
    }

    public static class Modes
    {
        // -------------------------------------------------------------------
        // Construction and wrap
        // -------------------------------------------------------------------

        @Test
        public void shouldWrapByteArray()
        {
            final byte[] array = new byte[64];
            final UnsafeBufferEx buffer = new UnsafeBufferEx(array);

            assertEquals(64, buffer.capacity());
            assertNotNull(buffer.segment());
            assertEquals(array, buffer.byteArray());
            assertEquals(0, buffer.wrapAdjustment());
            assertFalse(buffer.isExpandable());
        }

        @Test
        public void shouldWrapByteArrayWithOffset()
        {
            final byte[] array = new byte[64];
            final UnsafeBufferEx buffer = new UnsafeBufferEx(array, 8, 32);

            assertEquals(32, buffer.capacity());
            assertEquals(8, buffer.wrapAdjustment());
        }

        @Test
        public void shouldWrapDirectByteBuffer()
        {
            final ByteBuffer bb = ByteBuffer.allocateDirect(64);
            final UnsafeBufferEx buffer = new UnsafeBufferEx(bb);

            assertEquals(64, buffer.capacity());
            assertNull(buffer.byteArray());
            assertEquals(bb, buffer.byteBuffer());
        }

        @Test
        public void shouldWrapAnotherBufferBackedByArray()
        {
            final UnsafeBufferEx original = new UnsafeBufferEx(new byte[64]);
            original.putInt(0, 42);

            final UnsafeBufferEx copy = new UnsafeBufferEx(original);
            assertEquals(42, copy.getInt(0));
            assertEquals(64, copy.capacity());
        }

        @Test
        public void shouldWrapBufferBackedByDirectByteBuffer()
        {
            final UnsafeBufferEx source = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
            source.putInt(4, 456);

            final UnsafeBufferEx buffer = new UnsafeBufferEx(source);
            assertEquals(456, buffer.getInt(4));
        }

        @Test
        public void shouldWrapDefaultConstructor()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx();
            assertEquals(0, buffer.capacity());
        }

        @Test
        public void shouldReWrapFromHeapToDirect()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[32]);
            buffer.putInt(0, 7);
            assertEquals(7, buffer.getInt(0));

            buffer.wrap(ByteBuffer.allocateDirect(64));
            buffer.putInt(0, 9);
            assertEquals(9, buffer.getInt(0));
            assertEquals(64, buffer.capacity());
            assertNull(buffer.byteArray());
        }

        @Test
        public void shouldReWrapFromDirectToHeap()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(32));
            buffer.putInt(0, 7);
            assertEquals(7, buffer.getInt(0));

            buffer.wrap(new byte[64]);
            buffer.putInt(0, 9);
            assertEquals(9, buffer.getInt(0));
            assertEquals(64, buffer.capacity());
            assertNotNull(buffer.byteArray());
        }

        // -------------------------------------------------------------------
        // Cross-backing copy
        // -------------------------------------------------------------------

        @Test
        public void shouldCopyHeapBufferToDirectBuffer()
        {
            final UnsafeBufferEx heap = new UnsafeBufferEx(new byte[64]);
            heap.putInt(0, 0xDEADBEEF);
            heap.putLong(8, 0x1234567890ABCDEFL);

            final UnsafeBufferEx direct = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
            direct.putBytes(0, heap, 0, 16);

            assertEquals(0xDEADBEEF, direct.getInt(0));
            assertEquals(0x1234567890ABCDEFL, direct.getLong(8));
        }

        @Test
        public void shouldCopyDirectBufferToHeapBuffer()
        {
            final UnsafeBufferEx direct = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
            direct.putInt(0, 0xCAFEBABE);
            direct.putLong(8, 0xFEEDFACEFEEDFACEL);

            final UnsafeBufferEx heap = new UnsafeBufferEx(new byte[64]);
            heap.putBytes(0, direct, 0, 16);

            assertEquals(0xCAFEBABE, heap.getInt(0));
            assertEquals(0xFEEDFACEFEEDFACEL, heap.getLong(8));
        }

        // -------------------------------------------------------------------
        // Atomics — native (aligned) backings only
        // -------------------------------------------------------------------

        @Test
        public void shouldGetAndPutIntVolatile()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntVolatile(0, 42);
            assertEquals(42, buffer.getIntVolatile(0));
        }

        @Test
        public void shouldPutIntOrdered()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntOrdered(0, 99);
            assertEquals(99, buffer.getIntVolatile(0));
        }

        @Test
        public void shouldCompareAndSetInt()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntVolatile(0, 10);
            assertTrue(buffer.compareAndSetInt(0, 10, 20));
            assertEquals(20, buffer.getIntVolatile(0));

            assertFalse(buffer.compareAndSetInt(0, 10, 30));
        }

        @Test
        public void shouldGetAndAddInt()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntVolatile(0, 100);
            assertEquals(100, buffer.getAndAddInt(0, 50));
            assertEquals(150, buffer.getIntVolatile(0));
        }

        @Test
        public void shouldGetAndSetInt()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntVolatile(0, 100);
            assertEquals(100, buffer.getAndSetInt(0, 200));
            assertEquals(200, buffer.getIntVolatile(0));
        }

        @Test
        public void shouldAddIntOrdered()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntVolatile(0, 10);
            assertEquals(10, buffer.addIntOrdered(0, 5));
            assertEquals(15, buffer.getIntVolatile(0));
        }

        @Test
        public void shouldGetAndPutLongVolatile()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongVolatile(0, Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, buffer.getLongVolatile(0));
        }

        @Test
        public void shouldPutLongOrdered()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongOrdered(0, 123456789L);
            assertEquals(123456789L, buffer.getLongVolatile(0));
        }

        @Test
        public void shouldCompareAndSetLong()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongVolatile(0, 10L);
            assertTrue(buffer.compareAndSetLong(0, 10L, 20L));
            assertEquals(20L, buffer.getLongVolatile(0));

            assertFalse(buffer.compareAndSetLong(0, 10L, 30L));
        }

        @Test
        public void shouldGetAndAddLong()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongVolatile(0, 1000L);
            assertEquals(1000L, buffer.getAndAddLong(0, 500L));
            assertEquals(1500L, buffer.getLongVolatile(0));
        }

        @Test
        public void shouldGetAndSetLong()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongVolatile(0, 1000L);
            assertEquals(1000L, buffer.getAndSetLong(0, 2000L));
            assertEquals(2000L, buffer.getLongVolatile(0));
        }

        @Test
        public void shouldAddLongOrdered()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongVolatile(0, 100L);
            assertEquals(100L, buffer.addLongOrdered(0, 50L));
            assertEquals(150L, buffer.getLongVolatile(0));
        }

        @Test
        public void shouldGetAndPutLongAcquireRelease()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongRelease(0, 42L);
            assertEquals(42L, buffer.getLongAcquire(0));
        }

        @Test
        public void shouldGetAndPutLongOpaque()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongOpaque(0, 77L);
            assertEquals(77L, buffer.getLongOpaque(0));
        }

        @Test
        public void shouldGetAndPutIntAcquireRelease()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntRelease(0, 42);
            assertEquals(42, buffer.getIntAcquire(0));
        }

        @Test
        public void shouldGetAndPutIntOpaque()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntOpaque(0, 77);
            assertEquals(77, buffer.getIntOpaque(0));
        }

        @Test
        public void shouldCompareAndExchangeInt()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntVolatile(0, 10);
            assertEquals(10, buffer.compareAndExchangeInt(0, 10, 20));
            assertEquals(20, buffer.getIntVolatile(0));
        }

        @Test
        public void shouldCompareAndExchangeLong()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongVolatile(0, 100L);
            assertEquals(100L, buffer.compareAndExchangeLong(0, 100L, 200L));
            assertEquals(200L, buffer.getLongVolatile(0));
        }

        @Test
        public void shouldAddIntRelease()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntVolatile(0, 10);
            assertEquals(10, buffer.addIntRelease(0, 5));
            assertEquals(15, buffer.getIntVolatile(0));
        }

        @Test
        public void shouldAddLongRelease()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongVolatile(0, 10L);
            assertEquals(10L, buffer.addLongRelease(0, 5L));
            assertEquals(15L, buffer.getLongVolatile(0));
        }

        @Test
        public void shouldGetAndPutShortVolatile()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putShortVolatile(0, (short) 1234);
            assertEquals((short) 1234, buffer.getShortVolatile(0));
        }

        @Test
        public void shouldGetAndPutCharVolatile()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putCharVolatile(0, 'X');
            assertEquals('X', buffer.getCharVolatile(0));
        }

        @Test
        public void shouldGetAndPutByteVolatile()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putByteVolatile(0, (byte) 42);
            assertEquals((byte) 42, buffer.getByteVolatile(0));
        }

        @Test
        public void shouldVerifyAlignmentOnAlignedBuffer()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
            buffer.verifyAlignment();
        }

        // -------------------------------------------------------------------
        // Bounds checking — heap (bounded) path only
        // -------------------------------------------------------------------

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnGetIntOutOfBounds()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[8]);
            buffer.getInt(8);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnPutIntOutOfBounds()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[8]);
            buffer.putInt(8, 0);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnGetLongOutOfBounds()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[4]);
            buffer.getLong(0);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnNegativeIndex()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[64]);
            buffer.getInt(-1);
        }

        @Test
        public void shouldCheckLimit()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[16]);
            buffer.checkLimit(16);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnCheckLimitExceeded()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[16]);
            buffer.checkLimit(17);
        }

        @Test
        public void shouldBoundsCheck()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[16]);
            buffer.boundsCheck(0, 16);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnBoundsCheckExceeded()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[16]);
            buffer.boundsCheck(0, 17);
        }

        // -------------------------------------------------------------------
        // Heap atomics — unsupported (native-only): byte[] cannot satisfy
        // VarHandle alignment, so atomics throw rather than corrupt
        // -------------------------------------------------------------------

        @Test(expected = UnsupportedOperationException.class)
        public void shouldRejectPutLongOrderedOnHeap()
        {
            new UnsafeBufferEx(new byte[64]).putLongOrdered(8, 1L);
        }

        @Test(expected = UnsupportedOperationException.class)
        public void shouldRejectGetLongVolatileOnHeap()
        {
            new UnsafeBufferEx(new byte[64]).getLongVolatile(0);
        }

        @Test(expected = UnsupportedOperationException.class)
        public void shouldRejectCompareAndSetLongOnHeap()
        {
            new UnsafeBufferEx(new byte[64]).compareAndSetLong(0, 0L, 1L);
        }

        @Test(expected = UnsupportedOperationException.class)
        public void shouldRejectGetAndAddLongOnHeap()
        {
            new UnsafeBufferEx(new byte[64]).getAndAddLong(0, 1L);
        }

        @Test(expected = UnsupportedOperationException.class)
        public void shouldRejectPutIntOrderedOnHeap()
        {
            new UnsafeBufferEx(new byte[64]).putIntOrdered(4, 1);
        }

        @Test(expected = UnsupportedOperationException.class)
        public void shouldRejectGetAndAddIntOnHeap()
        {
            new UnsafeBufferEx(new byte[64]).getAndAddInt(0, 1);
        }

        // -------------------------------------------------------------------
        // Native bounds checks — enabled by default (regression: CountersLayout)
        // -------------------------------------------------------------------

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnPutLongOrderedOutOfBoundsNative()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(8));
            buffer.putLongOrdered(8, 1L);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnPutIntOutOfBoundsNative()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(8));
            buffer.putInt(8, 1);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnGetLongOutOfBoundsNative()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(8));
            buffer.getLong(4);
        }

        // -------------------------------------------------------------------
        // Release/acquire-only accessor lowering — fence-pair round-trips
        // -------------------------------------------------------------------
        // Cross-method coverage for the Lever 1 lowering: write via the release
        // arm of one method, read via the acquire arm of another. On x86 (default
        // zilla.buffer.aligned.atomics=false) the writer issues a releaseFence
        // before a plain unaligned store and the reader does a plain unaligned
        // load followed by an acquireFence; the fence pair must still preserve
        // the value.

        @Test
        public void shouldRoundTripLongOrderedToLongAcquire()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongOrdered(0, 0xCAFEBABEDEADBEEFL);
            assertEquals(0xCAFEBABEDEADBEEFL, buffer.getLongAcquire(0));
        }

        @Test
        public void shouldRoundTripLongReleaseToLongVolatile()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putLongRelease(0, 0x0102030405060708L);
            assertEquals(0x0102030405060708L, buffer.getLongVolatile(0));
        }

        @Test
        public void shouldRoundTripIntOrderedToIntAcquire()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntOrdered(0, 0xDEADBEEF);
            assertEquals(0xDEADBEEF, buffer.getIntAcquire(0));
        }

        @Test
        public void shouldRoundTripIntReleaseToIntVolatile()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));

            buffer.putIntRelease(0, 0xABCDEF01);
            assertEquals(0xABCDEF01, buffer.getIntVolatile(0));
        }

        // Bounds-check coverage on the lowered native arm — these accessors run
        // the SHOULD_BOUNDS_CHECK gate before the fence/store, so out-of-range
        // index must still throw IndexOutOfBoundsException.

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnPutLongReleaseOutOfBoundsNative()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(8));
            buffer.putLongRelease(8, 1L);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnGetLongAcquireOutOfBoundsNative()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(8));
            buffer.getLongAcquire(4);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnPutIntReleaseOutOfBoundsNative()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(4));
            buffer.putIntRelease(4, 1);
        }

        @Test(expected = IndexOutOfBoundsException.class)
        public void shouldThrowOnGetIntAcquireOutOfBoundsNative()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(4));
            buffer.getIntAcquire(2);
        }

        // -------------------------------------------------------------------
        // Comparable / equals / hashCode / toString
        // -------------------------------------------------------------------

        @Test
        public void shouldCompareBuffers()
        {
            final UnsafeBufferEx a = new UnsafeBufferEx(new byte[]{1, 2, 3});
            final UnsafeBufferEx b = new UnsafeBufferEx(new byte[]{1, 2, 4});
            final UnsafeBufferEx c = new UnsafeBufferEx(new byte[]{1, 2, 3});

            assertTrue(a.compareTo(b) < 0);
            assertTrue(b.compareTo(a) > 0);
            assertEquals(0, a.compareTo(c));
        }

        @Test
        public void shouldBeEqualForSameContent()
        {
            final UnsafeBufferEx a = new UnsafeBufferEx(new byte[]{1, 2, 3, 4});
            final UnsafeBufferEx b = new UnsafeBufferEx(new byte[]{1, 2, 3, 4});

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        public void shouldNotBeEqualForDifferentContent()
        {
            final UnsafeBufferEx a = new UnsafeBufferEx(new byte[]{1, 2, 3});
            final UnsafeBufferEx b = new UnsafeBufferEx(new byte[]{1, 2, 4});

            assertFalse(a.equals(b));
        }

        @Test
        public void shouldToString()
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[32]);
            assertEquals("UnsafeBufferEx{capacity=32}", buffer.toString());
        }
    }
}
