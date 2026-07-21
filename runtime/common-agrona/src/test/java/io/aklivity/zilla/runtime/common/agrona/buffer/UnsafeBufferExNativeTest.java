/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.junit.Test;

/**
 * Exercises {@link UnsafeBufferEx.Native}, the wrap-frozen specialization
 * returned from {@link UnsafeBufferEx#asNative()}. {@code Native} requires a
 * native (off-heap) {@link MemorySegment} and a direct {@link ByteBuffer};
 * every {@code wrap(...)} overload is intentionally unsupported so the JIT can
 * trust the final fields. These tests pin both the contract and the
 * round-trips against a peer {@link UnsafeBufferEx} on the same memory.
 */
public class UnsafeBufferExNativeTest
{
    @Test
    public void asNativeRejectsHeapByteArray()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        assertThrows(IllegalStateException.class, buffer::asNative);
    }

    @Test
    public void asNativeRejectsHeapByteBuffer()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocate(64));
        assertThrows(IllegalStateException.class, buffer::asNative);
    }

    @Test
    public void asNativeRejectsHeapMemorySegment()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx(MemorySegment.ofArray(new byte[64]));
        assertThrows(IllegalStateException.class, buffer::asNative);
    }

    @Test
    public void asNativeAcceptsDirectByteBuffer()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
        UnsafeBufferEx.Native native0 = buffer.asNative();
        assertEquals(64, native0.capacity());
    }

    @Test
    public void asNativeOnSubRangeWrapPreservesOffset()
    {
        ByteBuffer source = ByteBuffer.allocateDirect(64);
        UnsafeBufferEx whole = new UnsafeBufferEx(source);
        UnsafeBufferEx subRange = new UnsafeBufferEx(source, 16, 32);
        UnsafeBufferEx.Native native0 = subRange.asNative();

        assertEquals(32, native0.capacity());
        assertEquals(0, native0.wrapAdjustment());
        assertEquals(subRange.addressOffset(), native0.addressOffset());

        native0.putLong(0, 0x0123456789abcdefL);

        assertEquals(0x0123456789abcdefL, subRange.getLong(0));
        assertEquals(0x0123456789abcdefL, whole.getLong(16));
    }

    @Test
    public void putBytesOnSubRangeWrapUsesCorrectByteBufferOffset()
    {
        ByteBuffer source = ByteBuffer.allocateDirect(64);
        UnsafeBufferEx whole = new UnsafeBufferEx(source);
        UnsafeBufferEx subRange = new UnsafeBufferEx(source, 16, 32);
        UnsafeBufferEx.Native native0 = subRange.asNative();

        byte[] payload = "hello-sub-range-bytes".getBytes();
        native0.putBytes(0, payload);

        byte[] readback = new byte[payload.length];
        native0.getBytes(0, readback);
        assertArrayEquals(payload, readback);

        byte[] wholeReadback = new byte[payload.length];
        whole.getBytes(16, wholeReadback);
        assertArrayEquals(payload, wholeReadback);
    }

    @Test
    public void wrapByteArrayUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(new byte[64]));
    }

    @Test
    public void wrapByteArrayWithRangeUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(new byte[64], 0, 64));
    }

    @Test
    public void wrapByteBufferUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(ByteBuffer.allocateDirect(64)));
    }

    @Test
    public void wrapByteBufferWithRangeUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(ByteBuffer.allocateDirect(64), 0, 64));
    }

    @Test
    public void wrapDirectBufferUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        DirectBuffer other = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(other));
    }

    @Test
    public void wrapDirectBufferWithRangeUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        DirectBuffer other = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(other, 0, 64));
    }

    @Test
    public void wrapDirectBufferExUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        DirectBufferEx other = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(other));
    }

    @Test
    public void wrapDirectBufferExWithRangeUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        DirectBufferEx other = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(other, 0, 64));
    }

    @Test
    public void wrapAddressUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        assertThrows(UnsupportedOperationException.class, () -> native0.wrap(0L, 64));
    }

    @Test
    public void wrapMemorySegmentUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        try (Arena arena = Arena.ofConfined())
        {
            MemorySegment segment = arena.allocate(64);
            assertThrows(UnsupportedOperationException.class, () -> native0.wrap(segment));
        }
    }

    @Test
    public void wrapMemorySegmentWithRangeUnsupported()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        try (Arena arena = Arena.ofConfined())
        {
            MemorySegment segment = arena.allocate(64);
            assertThrows(UnsupportedOperationException.class, () -> native0.wrap(segment, 0, 64));
        }
    }

    @Test
    public void byteBufferIsIdentitySame()
    {
        ByteBuffer source = ByteBuffer.allocateDirect(64);
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(source).asNative();
        assertSame(source, native0.byteBuffer());
    }

    @Test
    public void primitiveRoundTripsAgreeWithUnsafeBufferEx()
    {
        ByteBuffer source = ByteBuffer.allocateDirect(64);
        UnsafeBufferEx peer = new UnsafeBufferEx(source);
        UnsafeBufferEx.Native native0 = peer.asNative();

        native0.putLong(0, 0x0123456789abcdefL);
        native0.putInt(8, 0x0a0b0c0d);
        native0.putShort(12, (short) 0x1234);
        native0.putByte(14, (byte) 0x7f);
        native0.putChar(16, 'Z');
        native0.putFloat(20, 3.14f);
        native0.putDouble(24, 2.71828);

        assertEquals(0x0123456789abcdefL, peer.getLong(0));
        assertEquals(0x0a0b0c0d, peer.getInt(8));
        assertEquals((short) 0x1234, peer.getShort(12));
        assertEquals((byte) 0x7f, peer.getByte(14));
        assertEquals('Z', peer.getChar(16));
        assertEquals(3.14f, peer.getFloat(20), 0.0f);
        assertEquals(2.71828, peer.getDouble(24), 0.0);

        assertEquals(0x0123456789abcdefL, native0.getLong(0));
        assertEquals(0x0a0b0c0d, native0.getInt(8));
        assertEquals((short) 0x1234, native0.getShort(12));
        assertEquals((byte) 0x7f, native0.getByte(14));
        assertEquals('Z', native0.getChar(16));
        assertEquals(3.14f, native0.getFloat(20), 0.0f);
        assertEquals(2.71828, native0.getDouble(24), 0.0);
    }

    @Test
    public void orderedAndVolatileAccessRoundTrip()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();

        native0.putLongOrdered(0, 0x1122334455667788L);
        native0.putIntOrdered(8, 0x44332211);
        native0.putLongVolatile(16, 0xdeadbeefcafebabeL);
        native0.putIntVolatile(24, 0xfeedface);

        assertEquals(0x1122334455667788L, native0.getLongVolatile(0));
        assertEquals(0x44332211, native0.getIntVolatile(8));
        assertEquals(0x1122334455667788L, native0.getLong(0));
        assertEquals(0x44332211, native0.getInt(8));
        assertEquals(0xdeadbeefcafebabeL, native0.getLongVolatile(16));
        assertEquals(0xfeedface, native0.getIntVolatile(24));
    }

    @Test
    public void atomicCompareAndSetSucceedsAndFails()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        native0.putLong(0, 1L);

        assertEquals(true, native0.compareAndSetLong(0, 1L, 2L));
        assertEquals(2L, native0.getLong(0));
        assertEquals(false, native0.compareAndSetLong(0, 1L, 3L));
        assertEquals(2L, native0.getLong(0));
    }

    @Test
    public void putBytesFromHeapByteBufferRoundTrips()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        byte[] payload = "hello-zilla-native".getBytes();
        ByteBuffer source = ByteBuffer.allocate(64);
        source.put(payload);
        source.flip();

        native0.putBytes(0, source, payload.length);

        byte[] readback = new byte[payload.length];
        native0.getBytes(0, readback);
        assertArrayEquals(payload, readback);
    }

    @Test
    public void putBytesFromDirectByteBufferRoundTrips()
    {
        UnsafeBufferEx.Native native0 = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        byte[] payload = "hello-direct-source".getBytes();
        ByteBuffer source = ByteBuffer.allocateDirect(64);
        source.put(payload);

        native0.putBytes(0, source, 0, payload.length);

        byte[] readback = new byte[payload.length];
        native0.getBytes(0, readback);
        assertArrayEquals(payload, readback);
    }

    @Test
    public void putBytesFromDirectBufferRoundTrips()
    {
        UnsafeBufferEx source = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
        byte[] payload = "hello-direct-buffer".getBytes();
        source.putBytes(0, payload);

        UnsafeBufferEx.Native target = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        target.putBytes(0, source, 0, payload.length);

        byte[] readback = new byte[payload.length];
        target.getBytes(0, readback);
        assertArrayEquals(payload, readback);
    }

    @Test
    public void putBytesFromDirectBufferExWithStaleSourceLimitRoundTrips()
    {
        // Agrona treats the source's NIO ByteBuffer limit as scratch state independent of
        // capacity (e.g. left dirty by CRC32.update); a putBytes spanning beyond the stale limit
        // but within capacity must still copy the full range — see KafkaCachePartition.computeHash
        ByteBuffer sourceBb = ByteBuffer.allocateDirect(64);
        UnsafeBufferEx source = new UnsafeBufferEx(sourceBb);
        byte[] payload = "trailer-spanning-stale-limit".getBytes();
        source.putBytes(0, payload);
        sourceBb.position(0);
        sourceBb.limit(4);

        UnsafeBufferEx.Native target = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        target.putBytes(0, source, 0, payload.length);

        byte[] readback = new byte[payload.length];
        target.getBytes(0, readback);
        assertArrayEquals(payload, readback);
    }

    @Test
    public void putBytesFromByteBufferWithStaleSourceLimitRoundTrips()
    {
        // the index-based putBytes(ByteBuffer) contract copies by capacity, not NIO limit; a stale
        // limit narrower than the requested range must not reject a valid copy
        ByteBuffer source = ByteBuffer.allocateDirect(64);
        byte[] payload = "bytebuffer-spanning-stale-limit".getBytes();
        source.put(payload);
        source.position(0);
        source.limit(4);

        UnsafeBufferEx.Native target = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        target.putBytes(0, source, 0, payload.length);

        byte[] readback = new byte[payload.length];
        target.getBytes(0, readback);
        assertArrayEquals(payload, readback);
    }

    @Test
    public void putBytesWithStaleDestinationLimitRoundTrips()
    {
        // ByteBuffer.put also bounds-checks the destination against its NIO limit; a write at an
        // index beyond a stale destination limit but within capacity must still succeed (e.g. a
        // mapped cache file whose limit was left dirty by CRC32.update)
        ByteBuffer destBb = ByteBuffer.allocateDirect(512);
        UnsafeBufferEx.Native target = new UnsafeBufferEx(destBb).asNative();
        destBb.position(0);
        destBb.limit(8);

        byte[] payload = "written-beyond-stale-dest-limit".getBytes();
        UnsafeBufferEx source = new UnsafeBufferEx(ByteBuffer.allocateDirect(64));
        source.putBytes(0, payload);

        target.putBytes(450, source, 0, payload.length);

        byte[] readback = new byte[payload.length];
        target.getBytes(450, readback);
        assertArrayEquals(payload, readback);
    }
}
