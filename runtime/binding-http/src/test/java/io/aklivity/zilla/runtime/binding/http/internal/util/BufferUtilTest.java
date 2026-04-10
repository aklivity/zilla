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
package io.aklivity.zilla.runtime.binding.http.internal.util;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class BufferUtilTest
{
    private static final byte[] CRLFCRLF = "\r\n\r\n".getBytes(US_ASCII);

    @Test
    public void shouldLocateLimitWhenValueAtEndBuffer()
    {
        DirectBufferEx buffer2 = new UnsafeBufferEx("a nice warm cookie cutter".getBytes(US_ASCII));
        assertEquals(buffer2.capacity(), BufferUtil.limitOfBytes(buffer2, 0, buffer2.capacity(), "cutter".getBytes()));
    }

    @Test
    public void shouldLocateLimitWhenValueInsideBuffer()
    {
        DirectBufferEx buffer2 = new UnsafeBufferEx("a nice warm cookie cutter".getBytes(US_ASCII));
        assertEquals("a nice".length(), BufferUtil.limitOfBytes(buffer2, 0, buffer2.capacity(), "nice".getBytes()));
    }

    @Test
    public void shouldReportLimitMinusOneWhenValueNotFound()
    {
        DirectBufferEx buffer2 = new UnsafeBufferEx("a nice warm cookie cutter".getBytes(US_ASCII));
        assertEquals(-1, BufferUtil.limitOfBytes(buffer2, 0, buffer2.capacity(), "cutlass".getBytes()));
    }

    @Test
    public void shouldReportLimitMinusOneWhenValueLongerThanBuffer()
    {
        DirectBufferEx buffer2 = new UnsafeBufferEx("a nice warm cookie cutter".getBytes(US_ASCII));
        assertEquals(-1, BufferUtil.limitOfBytes(buffer2, 0, buffer2.capacity(),
                "a nice warm cookie cutter indeed".getBytes()));
    }

    @Test
    public void shouldLocateLimitWhenValueInSecondBuffer()
    {
        DirectBufferEx buffer1 = new UnsafeBufferEx("".getBytes(US_ASCII));
        DirectBufferEx buffer2 = new UnsafeBufferEx("get / HTTP/1.1\r\n\r\n".getBytes(US_ASCII));
        assertEquals(buffer2.capacity(), BufferUtil.limitOfBytes(buffer1, 0, 0, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test
    public void shouldLocateFragmentedValueAtPosition1()
    {
        DirectBufferEx buffer1 = new UnsafeBufferEx("...\r\n\r".getBytes(US_ASCII));
        DirectBufferEx buffer2 = new UnsafeBufferEx("\n....".getBytes(US_ASCII));
        assertEquals(1, BufferUtil.limitOfBytes(buffer1, 3, 6, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test
    public void shouldLocateFragmentedValueAtPosition2()
    {
        DirectBufferEx buffer1 = new UnsafeBufferEx(".\r\n".getBytes(US_ASCII));
        DirectBufferEx buffer2 = new UnsafeBufferEx("\r\n....".getBytes(US_ASCII));
        assertEquals(2, BufferUtil.limitOfBytes(buffer1, 0, 3, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test
    public void shouldLocateFragmentedValueAtPosition3()
    {
        DirectBufferEx buffer1 = new UnsafeBufferEx("..\r".getBytes(US_ASCII));
        DirectBufferEx buffer2 = new UnsafeBufferEx("\n\r\n....".getBytes(US_ASCII));
        assertEquals(3, BufferUtil.limitOfBytes(buffer1, 0, 3, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test
    public void shouldLocateFragmentedValueAtPosition4()
    {
        DirectBufferEx buffer1 = new UnsafeBufferEx("...".getBytes(US_ASCII));
        DirectBufferEx buffer2 = new UnsafeBufferEx("\r\n\r\n....".getBytes(US_ASCII));
        assertEquals(4, BufferUtil.limitOfBytes(buffer1, 0, 3, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectValueWhollyInFragment()
    {
        DirectBufferEx buffer1 = new UnsafeBufferEx("\r\n\r\n".getBytes(US_ASCII));
        DirectBufferEx buffer2 = new UnsafeBufferEx("...\r\n\r\n....".getBytes(US_ASCII));
        assertEquals(4, BufferUtil.limitOfBytes(buffer1, 0, 4, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

}

