/*
 * Copyright 2021-2023 Aklivity Inc.
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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class BufferUtilTest
{
    private static final byte[] CRLFCRLF = "\r\n\r\n".getBytes(US_ASCII);

    @Test
    public void shouldLocateLimitWhenValueAtEndBuffer()
    {
        DirectBuffer buffer2 = new UnsafeBuffer("a nice warm cookie cutter".getBytes(US_ASCII));
        assertEquals(buffer2.capacity(), BufferUtil.limitOfBytes(buffer2, 0, buffer2.capacity(), "cutter".getBytes()));
    }

    @Test
    public void shouldLocateLimitWhenValueInsideBuffer()
    {
        DirectBuffer buffer2 = new UnsafeBuffer("a nice warm cookie cutter".getBytes(US_ASCII));
        assertEquals("a nice".length(), BufferUtil.limitOfBytes(buffer2, 0, buffer2.capacity(), "nice".getBytes()));
    }

    @Test
    public void shouldReportLimitMinusOneWhenValueNotFound()
    {
        DirectBuffer buffer2 = new UnsafeBuffer("a nice warm cookie cutter".getBytes(US_ASCII));
        assertEquals(-1, BufferUtil.limitOfBytes(buffer2, 0, buffer2.capacity(), "cutlass".getBytes()));
    }

    @Test
    public void shouldReportLimitMinusOneWhenValueLongerThanBuffer()
    {
        DirectBuffer buffer2 = new UnsafeBuffer("a nice warm cookie cutter".getBytes(US_ASCII));
        assertEquals(-1, BufferUtil.limitOfBytes(buffer2, 0, buffer2.capacity(),
                "a nice warm cookie cutter indeed".getBytes()));
    }

    @Test
    public void shouldLocateLimitWhenValueInSecondBuffer()
    {
        DirectBuffer buffer1 = new UnsafeBuffer("".getBytes(US_ASCII));
        DirectBuffer buffer2 = new UnsafeBuffer("get / HTTP/1.1\r\n\r\n".getBytes(US_ASCII));
        assertEquals(buffer2.capacity(), BufferUtil.limitOfBytes(buffer1, 0, 0, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test
    public void shouldLocateFragmentedValueAtPosition1()
    {
        DirectBuffer buffer1 = new UnsafeBuffer("...\r\n\r".getBytes(US_ASCII));
        DirectBuffer buffer2 = new UnsafeBuffer("\n....".getBytes(US_ASCII));
        assertEquals(1, BufferUtil.limitOfBytes(buffer1, 3, 6, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test
    public void shouldLocateFragmentedValueAtPosition2()
    {
        DirectBuffer buffer1 = new UnsafeBuffer(".\r\n".getBytes(US_ASCII));
        DirectBuffer buffer2 = new UnsafeBuffer("\r\n....".getBytes(US_ASCII));
        assertEquals(2, BufferUtil.limitOfBytes(buffer1, 0, 3, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test
    public void shouldLocateFragmentedValueAtPosition3()
    {
        DirectBuffer buffer1 = new UnsafeBuffer("..\r".getBytes(US_ASCII));
        DirectBuffer buffer2 = new UnsafeBuffer("\n\r\n....".getBytes(US_ASCII));
        assertEquals(3, BufferUtil.limitOfBytes(buffer1, 0, 3, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test
    public void shouldLocateFragmentedValueAtPosition4()
    {
        DirectBuffer buffer1 = new UnsafeBuffer("...".getBytes(US_ASCII));
        DirectBuffer buffer2 = new UnsafeBuffer("\r\n\r\n....".getBytes(US_ASCII));
        assertEquals(4, BufferUtil.limitOfBytes(buffer1, 0, 3, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectValueWhollyInFragment()
    {
        DirectBuffer buffer1 = new UnsafeBuffer("\r\n\r\n".getBytes(US_ASCII));
        DirectBuffer buffer2 = new UnsafeBuffer("...\r\n\r\n....".getBytes(US_ASCII));
        assertEquals(4, BufferUtil.limitOfBytes(buffer1, 0, 4, buffer2, 0, buffer2.capacity(), CRLFCRLF));
    }

}

