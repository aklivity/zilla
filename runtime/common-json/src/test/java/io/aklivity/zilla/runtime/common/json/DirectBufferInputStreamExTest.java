/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class DirectBufferInputStreamExTest
{
    @Test
    void shouldReadAllBytesThenEof() throws Exception
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("hello".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, buffer.capacity());

        assertEquals('h', in.read());
        assertEquals('e', in.read());
        assertEquals('l', in.read());
        assertEquals('l', in.read());
        assertEquals('o', in.read());
        assertEquals(-1, in.read());
    }

    @Test
    void shouldSupportMark()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("abcdef".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, buffer.capacity());

        assertTrue(in.markSupported());
    }

    @Test
    void shouldResetToMarkedPosition() throws Exception
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("abcdef".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, buffer.capacity());

        assertEquals('a', in.read());
        assertEquals('b', in.read());
        in.mark(100);
        assertEquals('c', in.read());
        assertEquals('d', in.read());
        in.reset();
        assertEquals('c', in.read());
        assertEquals('d', in.read());
        assertEquals('e', in.read());
    }

    @Test
    void shouldReadBulk() throws Exception
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("abcdef".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, buffer.capacity());

        byte[] dst = new byte[4];
        int read = in.read(dst, 0, dst.length);
        assertEquals(4, read);
        assertArrayEquals("abcd".getBytes(UTF_8), dst);

        byte[] dst2 = new byte[4];
        int read2 = in.read(dst2, 0, dst2.length);
        assertEquals(2, read2);
        assertEquals('e', dst2[0]);
        assertEquals('f', dst2[1]);

        assertEquals(-1, in.read());
    }

    @Test
    void shouldReportBulkReadEofWhenExhausted() throws Exception
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("ab".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, buffer.capacity());

        byte[] dst = new byte[8];
        assertEquals(2, in.read(dst, 0, dst.length));
        assertEquals(-1, in.read(dst, 0, dst.length));
    }

    @Test
    void shouldReportAvailable() throws Exception
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("abcdef".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, buffer.capacity());

        assertEquals(6, in.available());
        in.read();
        assertEquals(5, in.available());
    }

    @Test
    void shouldSkipBytes()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("abcdef".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, buffer.capacity());

        assertEquals(3, in.skip(3));
        assertEquals('d', in.read());
    }

    @Test
    void shouldSkipClampsToAvailable()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("abc".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, buffer.capacity());

        assertEquals(3, in.skip(10));
        assertEquals(-1, in.read());
    }

    @Test
    void shouldReadFromBufferOffset()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("xxhelloxx".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 2, 5);

        assertEquals('h', in.read());
        assertEquals('e', in.read());
        assertEquals('l', in.read());
        assertEquals('l', in.read());
        assertEquals('o', in.read());
        assertEquals(-1, in.read());
    }

    @Test
    void shouldExposeBufferOffsetAndLength()
    {
        UnsafeBufferEx buffer = new UnsafeBufferEx("abcdef".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 2, 3);

        assertSame(buffer, in.buffer());
        assertEquals(2, in.offset());
        assertEquals(3, in.length());
    }

    @Test
    void shouldRewrapResetsPosition() throws Exception
    {
        UnsafeBufferEx first = new UnsafeBufferEx("abc".getBytes(UTF_8));
        UnsafeBufferEx second = new UnsafeBufferEx("xyz".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(first, 0, first.capacity());
        assertEquals('a', in.read());

        in.wrap(second, 0, second.capacity());
        assertEquals('x', in.read());
        assertEquals('y', in.read());
        assertEquals('z', in.read());
        assertEquals(-1, in.read());
    }

    @Test
    void shouldRewrapResetsMark()
    {
        UnsafeBufferEx first = new UnsafeBufferEx("abcdef".getBytes(UTF_8));
        UnsafeBufferEx second = new UnsafeBufferEx("xyz".getBytes(UTF_8));
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(first, 0, first.capacity());
        in.read();
        in.read();
        in.mark(10);

        in.wrap(second, 0, second.capacity());
        in.read();
        in.reset();
        assertEquals('x', in.read());
    }

    @Test
    void shouldSupportClose() throws Exception
    {
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.close();
    }
}
