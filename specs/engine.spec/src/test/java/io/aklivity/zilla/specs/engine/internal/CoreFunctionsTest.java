/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.specs.engine.internal;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.internal.types.String16FW;
import io.aklivity.zilla.specs.engine.internal.types.String8FW;
import io.aklivity.zilla.specs.engine.internal.types.Varuint32nFW;

public class CoreFunctionsTest
{
    @Test
    public void shouldEncodeString()
    {
        byte[] array = CoreFunctions.string("value");

        DirectBuffer buffer = new UnsafeBuffer(array);
        String8FW string = new String8FW().wrap(buffer, 0, buffer.capacity());

        assertEquals("value", string.asString());
    }

    @Test
    public void shouldEncodeNullString()
    {
        byte[] array = CoreFunctions.string(null);

        DirectBuffer buffer = new UnsafeBuffer(array);
        String8FW string = new String8FW().wrap(buffer, 0, buffer.capacity());

        assertNull(string.asString());
    }

    @Test
    public void shouldEncodeEmptyString()
    {
        byte[] array = CoreFunctions.string("");

        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[array.length + 1]);
        buffer.putBytes(0, array);
        String8FW string = new String8FW().wrap(buffer, 0, buffer.capacity());

        assertEquals("", string.asString());
    }

    @Test
    public void shouldEncodeString16()
    {
        byte[] array = CoreFunctions.string16("value");

        DirectBuffer buffer = new UnsafeBuffer(array);
        String16FW string = new String16FW().wrap(buffer, 0, buffer.capacity());

        assertEquals("value", string.asString());
    }

    @Test
    public void shouldEncodeNullString16()
    {
        byte[] array = CoreFunctions.string16(null);

        DirectBuffer buffer = new UnsafeBuffer(array);
        String16FW string = new String16FW().wrap(buffer, 0, buffer.capacity());

        assertNull(string.asString());
    }

    @Test
    public void shouldEncodeEmptyString16()
    {
        byte[] array = CoreFunctions.string16("");

        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[array.length + 1]);
        buffer.putBytes(0, array);
        String16FW string = new String16FW().wrap(buffer, 0, buffer.capacity());

        assertEquals("", string.asString());
    }

    @Test
    public void shouldEncodeString16n()
    {
        byte[] array = CoreFunctions.string16n("value");

        DirectBuffer buffer = new UnsafeBuffer(array);
        String16FW string = new String16FW(BIG_ENDIAN).wrap(buffer, 0, buffer.capacity());

        assertEquals("value", string.asString());
    }

    @Test
    public void shouldEncodeVarString()
    {
        byte[] array = CoreFunctions.varstring("value");

        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[array.length + 1]);
        buffer.putBytes(0, array);
        Varuint32nFW length = new Varuint32nFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(5, length.value());
        assertEquals(array.length, length.limit() + length.value());
        assertEquals("value", buffer.getStringWithoutLengthUtf8(length.limit(), length.value()));
    }

    @Test
    public void shouldEncodeNullVarString()
    {
        byte[] array = CoreFunctions.varstring(null);

        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[array.length + 1]);
        buffer.putBytes(0, array);
        Varuint32nFW length = new Varuint32nFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(-1, length.value());
        assertEquals(array.length, length.limit());
    }

    @Test
    public void shouldEncodeEmptyVarString()
    {
        byte[] array = CoreFunctions.varstring("");

        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[array.length + 1]);
        buffer.putBytes(0, array);
        Varuint32nFW length = new Varuint32nFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, length.value());
        assertEquals(array.length, length.limit() + length.value());
    }

    @Test
    public void shouldMaskChallengeCapability()
    {
        final byte challengeMask = CoreFunctions.capabilities("CHALLENGE");
        assertEquals(0x01, challengeMask);
    }

    @Test
    public void shouldComputeVarintTenBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(Long.MAX_VALUE);
        assertArrayEquals(new byte[] { (byte) 0xfe, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintTenBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 62);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintNineBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 62);
        assertArrayEquals(new byte[] { (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintNineBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 55);
        assertArrayEquals(new byte[] { (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintEightBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 55);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintEightBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 48);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintSevenBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 48);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintSevenBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 41);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintSixBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 41);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintSixBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 34);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintFiveBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 34);
        assertArrayEquals(new byte[] { (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintFiveBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 27);
        assertArrayEquals(new byte[] { (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintFourBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 27);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintFourBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 20);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintThreeBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 20);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintThreeBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 13);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintTwoBytesMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 13);
        assertArrayEquals(new byte[] { (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintTwoBytesMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(1L << 6);
        assertArrayEquals(new byte[] { (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintOneByteMax() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(-1L << 6);
        assertArrayEquals(new byte[] { 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintOneByteMin() throws Exception
    {
        byte[] actuals = CoreFunctions.varint(0);
        assertArrayEquals(new byte[] { 0x00 }, actuals);
    }
}
