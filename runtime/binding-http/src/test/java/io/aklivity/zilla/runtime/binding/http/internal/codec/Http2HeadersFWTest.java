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
package io.aklivity.zilla.runtime.binding.http.internal.codec;

import static io.aklivity.zilla.runtime.binding.http.internal.codec.Http2FrameType.HEADERS;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.INCREMENTAL_INDEXING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext;
import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackHeaderBlockFW;
import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackHeaderBlockFWTest;

public class Http2HeadersFWTest
{

    @Test
    public void decode()
    {
        byte[] bytes = new byte[]
        {
            0x7f, 0x7f,
            // HEADERS frame begin
            0x00, 0x00, 0x0f, 0x01, 0x05, 0x00, 0x00, 0x00, 0x01, (byte) 0x82, (byte) 0x86,
            (byte) 0x84, 0x41, (byte) 0x8a, 0x08, (byte) 0x9d, 0x5c, 0x0b, (byte) 0x81, 0x70,
            (byte) 0xdc, 0x78, 0x0f, 0x03,
            // HEADERS frame end
            0x7f, 0x7f
        };

        DirectBuffer buffer = new UnsafeBuffer(bytes);
        Http2HeadersFW fw = new Http2HeadersFW().wrap(buffer, 2, buffer.capacity());  // non-zero offset
        assertEquals(26, fw.limit());
        assertTrue(fw.endStream());
        assertTrue(fw.endHeaders());
        assertFalse(fw.priority());
        assertFalse(fw.padded());

        Map<String, String> headers = new LinkedHashMap<>();
        HpackContext hpackContext = new HpackContext();
        HpackHeaderBlockFW block = new HpackHeaderBlockFW().wrap(
                fw.buffer(), fw.dataOffset(), fw.dataOffset() + fw.dataLength());
        block.forEach(HpackHeaderBlockFWTest.getHeaders(hpackContext, headers));

        assertEquals(4, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("http", headers.get(":scheme"));
        assertEquals("/", headers.get(":path"));
        assertEquals("127.0.0.1:8080", headers.get(":authority"));
    }

    @Test
    public void encode()
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        Http2HeadersFW fw = new Http2HeadersFW.Builder()
                .wrap(buf, 1, buf.capacity())   // non-zero offset
                .header(h -> h.indexed(2))      // :method: GET
                .header(h -> h.indexed(6))      // :scheme: http
                .header(h -> h.indexed(4))      // :path: /
                .header(h -> h.literal(l -> l.type(INCREMENTAL_INDEXING).name(1).value("www.example.com")))
                .endHeaders()
                .streamId(3)
                .build();

        assertEquals(20, fw.length());
        assertEquals(1, fw.offset());
        assertEquals(30, fw.limit());
        assertTrue(fw.endHeaders());
        assertEquals(HEADERS, fw.type());
        assertEquals(3, fw.streamId());

        Map<String, String> headers = new LinkedHashMap<>();
        HpackContext context = new HpackContext();
        HpackHeaderBlockFW block = new HpackHeaderBlockFW().wrap(
                fw.buffer(), fw.dataOffset(), fw.dataOffset() + fw.dataLength());
        block.forEach(HpackHeaderBlockFWTest.getHeaders(context, headers));

        assertEquals(4, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("http", headers.get(":scheme"));
        assertEquals("/", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
    }

}
