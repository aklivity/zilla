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
package io.aklivity.zilla.runtime.binding.http.internal.hpack;

import static org.junit.Assert.assertEquals;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class HpackIntegerFWTest
{

    // C.1.1. Encoding 10 Using a 5-Bit Prefix
    @Test
    public void encode()
    {
        int value = 10;
        int n = 5;
        byte[] bytes = new byte[100];
        bytes[1] = (byte) 0xe0;
        MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackIntegerFW.Builder builder = new HpackIntegerFW.Builder(n);
        HpackIntegerFW fw = builder
                .wrap(buffer, 1, buffer.capacity())
                .integer(value)
                .build();
        assertEquals((byte) 0xea, bytes[1]);

        assertEquals(value, fw.integer());
        assertEquals(2, fw.limit());
        assertEquals(1, fw.sizeof());
    }

    // C.1.1. Decoding 10 Using a 5-Bit Prefix
    @Test
    public void decode()
    {
        // Decoding 10 Using a 5-Bit Prefix
        int value = 10;
        int n = 5;
        byte[] bytes = new byte[100];
        bytes[1] = (byte) 0xea;
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackIntegerFW fw = new HpackIntegerFW(n);
        int got = fw
                .wrap(buffer, 1, buffer.capacity())
                .integer();

        assertEquals(value, got);
        assertEquals(2, fw.limit());
        assertEquals(1, fw.sizeof());
    }

    // C.1.2. Encoding 1337 Using a 5-Bit Prefix
    @Test
    public void encode1()
    {
        int value = 1337;
        int n = 5;
        byte[]bytes = new byte[100];
        bytes[1] = (byte) 0x00;
        MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackIntegerFW.Builder builder = new HpackIntegerFW.Builder(n);
        HpackIntegerFW fw = builder
                .wrap(buffer, 1, buffer.capacity())
                .integer(value)
                .build();
        assertEquals((byte) 0x1f, bytes[1]);
        assertEquals((byte) 0x9a, bytes[2]);
        assertEquals((byte) 0x0a, bytes[3]);

        assertEquals(value, fw.integer());
        assertEquals(4, fw.limit());
        assertEquals(3, fw.sizeof());
    }

    // C.1.2. Decoding 1337 Using a 5-Bit Prefix
    @Test
    public void decode1()
    {
        int value = 1337;
        int n = 5;
        byte[]bytes = new byte[100];
        bytes[1] = (byte) 0x1f;
        bytes[2] = (byte) 0x9a;
        bytes[3] = (byte) 0x0a;
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackIntegerFW fw = new HpackIntegerFW(n);
        int got = fw
                .wrap(buffer, 1, buffer.capacity())
                .integer();

        assertEquals(value, got);
        assertEquals(4, fw.limit());
        assertEquals(3, fw.sizeof());
    }

    @Test
    public void encode2()
    {
        // Encoding 1337 Using a 5-Bit Prefix
        int value = 1337;
        int n = 5;
        byte[] bytes = new byte[100];
        bytes[1] = (byte) 0xe0;
        MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackIntegerFW.Builder builder = new HpackIntegerFW.Builder(n);
        HpackIntegerFW fw = builder
                .wrap(buffer, 1, buffer.capacity())
                .integer(value)
                .build();
        assertEquals((byte) 0xff, bytes[1]);
        assertEquals((byte) 0x9a, bytes[2]);
        assertEquals((byte) 0x0a, bytes[3]);

        assertEquals(value, fw.integer());
        assertEquals(4, fw.limit());
        assertEquals(3, fw.sizeof());
    }

    @Test
    public void decode2()
    {
        // Decoding 1337 Using a 5-Bit Prefix
        int value = 1337;
        int n = 5;
        byte[] bytes = new byte[100];
        bytes[1] = (byte) 0xff;
        bytes[2] = (byte) 0x9a;
        bytes[3] = (byte) 0x0a;
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackIntegerFW fw = new HpackIntegerFW(n);
        int got = fw
                .wrap(buffer, 1, buffer.capacity())
                .integer();

        assertEquals(value, got);
        assertEquals(4, fw.limit());
        assertEquals(3, fw.sizeof());
    }

    // C.1.3.  Encoding 42 Starting at an Octet Boundary
    @Test
    public void encode3()
    {
        // Encoding 42 Starting at an Octet Boundary (n = 8)
        int value = 42;
        int n = 8;
        byte[] bytes = new byte[100];
        MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackIntegerFW.Builder builder = new HpackIntegerFW.Builder(n);
        HpackIntegerFW fw = builder
                .wrap(buffer, 2, buffer.capacity())
                .integer(value)
                .build();
        assertEquals((byte) 0x2a, bytes[2]);

        assertEquals(value, fw.integer());
        assertEquals(3, fw.limit());
        assertEquals(1, fw.sizeof());
    }

    // C.1.3. Decoding 42 Starting at an Octet Boundary
    @Test
    public void decode3()
    {
        // Decoding 42 Starting at an Octet Boundary (n = 8)
        int value = 42;
        int n = 8;
        byte[] bytes = new byte[100];
        bytes[2] = 0x2a;
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackIntegerFW fw = new HpackIntegerFW(n);
        int got = fw
                .wrap(buffer, 2, buffer.capacity())
                .integer();

        assertEquals(value, got);
        assertEquals(3, fw.limit());
        assertEquals(1, fw.sizeof());
    }
}
