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
package io.aklivity.zilla.runtime.cog.http.internal.hpack;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.stream.IntStream;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class HpackStringFWTest
{

    @Test
    public void encode()
    {
        String value = "custom-key";
        byte[] valueBytes = value.getBytes(US_ASCII);
        DirectBuffer valueBuf = new UnsafeBuffer(valueBytes);
        byte[] bytes = new byte[100];

        MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackStringFW.Builder builder = new HpackStringFW.Builder();
        HpackStringFW fw = builder
                .wrap(buffer, 1, buffer.capacity())
                .string(value)
                .build();
        assertEquals((byte) 0x0a, bytes[1]);

        assertFalse(fw.huffman());
        assertEquals(valueBuf, fw.payload());
        assertEquals(value.length() + 2, fw.limit());
    }

    @Test
    public void decode()
    {
        String value = "custom-key";
        byte[] valueBytes = value.getBytes(US_ASCII);
        DirectBuffer valueBuf = new UnsafeBuffer(valueBytes);
        byte[] bytes = new byte[100];
        bytes[1] = (byte) valueBytes.length;
        System.arraycopy(valueBytes, 0, bytes, 2, value.length());

        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackStringFW fw = new HpackStringFW()
                .wrap(buffer, 1, buffer.capacity());

        assertEquals(valueBuf, fw.payload());
        assertEquals(value.length() + 2, fw.limit());
    }

    @Test
    public void encode1()
    {
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, 1337).forEach(x -> sb.append("a"));
        String value = sb.toString();
        byte[] valueBytes = value.getBytes(US_ASCII);
        DirectBuffer valueBuf = new UnsafeBuffer(valueBytes);
        byte[] bytes = new byte[2048];

        MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackStringFW.Builder builder = new HpackStringFW.Builder();
        HpackStringFW fw = builder
                .wrap(buffer, 1, buffer.capacity())
                .string(value)
                .build();
        assertEquals((byte) 0x7f, bytes[1]);
        assertEquals((byte) 0xba, bytes[2]);
        assertEquals((byte) 0x09, bytes[3]);

        assertFalse(fw.huffman());
        assertEquals(valueBuf, fw.payload());
        assertEquals(value.length() + 4, fw.limit());
    }

    @Test
    public void decode1()
    {
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, 1337).forEach(x -> sb.append("a"));
        String value = sb.toString();
        byte[] valueBytes = value.getBytes(US_ASCII);
        DirectBuffer valueBuf = new UnsafeBuffer(valueBytes);
        byte[] bytes = new byte[2048];
        bytes[1] = (byte) 0x7f;
        bytes[2] = (byte) 0xba;
        bytes[3] = (byte) 0x09;
        System.arraycopy(valueBytes, 0, bytes, 4, value.length());

        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackStringFW fw = new HpackStringFW()
                .wrap(buffer, 1, buffer.capacity());

        assertEquals(valueBuf, fw.payload());
        assertEquals(value.length() + 4, fw.limit());
    }

    // Encodes string (passed as DirectBuffer)
    @Test
    public void encode2()
    {
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, 1337).forEach(x -> sb.append("a"));
        String value = sb.toString();

        byte[] valueBytes = value.getBytes(US_ASCII);
        DirectBuffer valueBuf = new UnsafeBuffer(valueBytes);
        byte[] bytes = new byte[2048];

        MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackStringFW.Builder builder = new HpackStringFW.Builder();
        HpackStringFW fw = builder
                .wrap(buffer, 1, buffer.capacity())
                .string(valueBuf, 0, valueBuf.capacity())
                .build();
        assertEquals((byte) 0x7f, bytes[1]);
        assertEquals((byte) 0xba, bytes[2]);
        assertEquals((byte) 0x09, bytes[3]);

        assertFalse(fw.huffman());
        assertEquals(valueBuf, fw.payload());
        assertEquals(value.length() + 4, fw.limit());
    }
}
