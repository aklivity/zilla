/*
 * Copyright 2021-2021 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.cog.http.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.INCREMENTAL_INDEXING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.cog.http.internal.hpack.HpackHeaderFieldFW.HeaderFieldType;

public class HpackHeaderBlockFWTest
{

    // Test for decoding "C.3.  Request Examples without Huffman Coding"
    @Test
    public void decodeC3()
    {
        HpackContext context = new HpackContext();

        // First request
        decodeC31(context);

        // Second request
        decodeC32(context);

        // Third request
        decodeC33(context);
    }

    // Decoding "C.3.1.  First Request"
    private void decodeC31(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "828684410f7777772e6578616d706c652e636f6d" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(21, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(4, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("http", headers.get(":scheme"));
        assertEquals("/", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
    }

    // Decoding "C.3.2.  Second Request"
    private void decodeC32(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "828684be58086e6f2d6361636865" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(15, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(5, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("http", headers.get(":scheme"));
        assertEquals("/", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
        assertEquals("no-cache", headers.get("cache-control"));
    }

    // Decoding "C.3.3.  Third Request"
    private void decodeC33(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "828785bf400a637573746f6d2d6b65790c637573746f6d2d76616c7565" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(30, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(5, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("https", headers.get(":scheme"));
        assertEquals("/index.html", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
        assertEquals("custom-value", headers.get("custom-key"));
    }

    // Test for encoding "C.3.  Request Examples without Huffman Coding"
    @Test
    public void encodeC3()
    {
        HpackContext context = new HpackContext();

        // First request
        encodeC31(context);

        // Second request
        encodeC32(context);

        // Third request
        encodeC33(context);
    }

    // Encoding "C.3.1.  First Request"
    private void encodeC31(HpackContext context)
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderBlockFW fw = new HpackHeaderBlockFW.Builder()
                .wrap(buf, 1, buf.capacity())
                .header(h -> h.indexed(2))      // :method: GET
                .header(h -> h.indexed(6))      // :scheme: http
                .header(h -> h.indexed(4))      // :path: /
                .header(h -> h.literal(l -> l.type(INCREMENTAL_INDEXING).name(1).value("www.example.com")))
                .build();

        assertEquals(21, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(4, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("http", headers.get(":scheme"));
        assertEquals("/", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
    }

    // Encoding "C.3.2.  Second Request"
    private void encodeC32(HpackContext context)
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderBlockFW fw = new HpackHeaderBlockFW.Builder()
                .wrap(buf, 1, buf.capacity())
                .header(h -> h.indexed(2))        // :method: GET
                .header(h -> h.indexed(6))        // :scheme: http
                .header(h -> h.indexed(4))        // :path: /
                .header(h -> h.indexed(62))       // :authority: www.example.com
                .header(h -> h.literal(l -> l.type(INCREMENTAL_INDEXING).name(24).value("no-cache")))
                .build();
        assertEquals(15, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(5, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("http", headers.get(":scheme"));
        assertEquals("/", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
        assertEquals("no-cache", headers.get("cache-control"));
    }

    // Encoding "C.3.3.  Third Request"
    private void encodeC33(HpackContext context)
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderBlockFW fw = new HpackHeaderBlockFW.Builder()
                .wrap(buf, 1, buf.capacity())
                .header(h -> h.indexed(2))    // :method: GET
                .header(h -> h.indexed(7))    // :scheme: https
                .header(h -> h.indexed(5))    // :path: /index.html
                .header(h -> h.indexed(63))   // :authority: www.example.com
                .header(h -> h.literal(l -> l.type(INCREMENTAL_INDEXING).name("custom-key").value("custom-value")))
                .build();
        assertEquals(30, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(5, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("https", headers.get(":scheme"));
        assertEquals("/index.html", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
        assertEquals("custom-value", headers.get("custom-key"));
    }

    // Test for decoding "C.4.  Request Examples with Huffman Coding"
    @Test
    public void decodeC4()
    {
        HpackContext context = new HpackContext();

        // First request
        decodeC41(context);

        // Second request
        decodeC42(context);

        // Third request
        decodeC43(context);
    }

    // Decoding "C.4.1.  First Request"
    private void decodeC41(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "828684418cf1e3c2e5f23a6ba0ab90f4ff" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(18, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(4, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("http", headers.get(":scheme"));
        assertEquals("/", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
    }

    // Decoding "C.4.2.  Second Request"
    private void decodeC42(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "828684be5886a8eb10649cbf" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(13, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(5, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("http", headers.get(":scheme"));
        assertEquals("/", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
        assertEquals("no-cache", headers.get("cache-control"));
    }

    // Decoding "C.4.3.  Third Request"
    private void decodeC43(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "828785bf408825a849e95ba97d7f8925a849e95bb8e8b4bf" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(25, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(5, headers.size());
        assertEquals("GET", headers.get(":method"));
        assertEquals("https", headers.get(":scheme"));
        assertEquals("/index.html", headers.get(":path"));
        assertEquals("www.example.com", headers.get(":authority"));
        assertEquals("custom-value", headers.get("custom-key"));
    }




    // Test for encoding "C.3.  Request Examples without Huffman Coding"
    @Test
    public void decodeC5()
    {
        HpackContext context = new HpackContext(256, false);

        // First response
        decodeC51(context);

        // Second response
        decodeC52(context);

        // Third request
        decodeC53(context);
    }

    // Decoding "C.5.1.  First Response"
    private void decodeC51(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "4803333032580770726976617465611d" +
                        "4d6f6e2c203231204f63742032303133" +
                        "2032303a31333a323120474d546e1768" +
                        "747470733a2f2f7777772e6578616d70" +
                        "6c652e636f6d" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(71, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(4, headers.size());
        assertEquals("302", headers.get(":status"));
        assertEquals("private", headers.get("cache-control"));
        assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", headers.get("date"));
        assertEquals("https://www.example.com", headers.get("location"));

        assertEquals(4, context.table.size());
        assertEquals(222, context.tableSize);
        assertEquals("location", context.name(62));
        assertEquals("https://www.example.com", context.value(62));
        assertEquals("date", context.name(63));
        assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", context.value(63));
        assertEquals("cache-control", context.name(64));
        assertEquals("private", context.value(64));
        assertEquals(":status", context.name(65));
        assertEquals("302", context.value(65));
    }

    // Decoding "C.5.2.  Second Response"
    private void decodeC52(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "4803333037c1c0bf" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(9, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(4, headers.size());
        assertEquals("307", headers.get(":status"));
        assertEquals("private", headers.get("cache-control"));
        assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", headers.get("date"));
        assertEquals("https://www.example.com", headers.get("location"));

        assertEquals(4, context.table.size());
        assertEquals(222, context.tableSize);
        assertEquals(":status", context.name(62));
        assertEquals("307", context.value(62));
        assertEquals("location", context.name(63));
        assertEquals("https://www.example.com", context.value(63));
        assertEquals("date", context.name(64));
        assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", context.value(64));
        assertEquals("cache-control", context.name(65));
        assertEquals("private", context.value(65));
    }

    // Decoding "C.5.3.  Third Response"
    private void decodeC53(HpackContext context)
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header list begin
                        "88c1611d4d6f6e2c203231204f637420" +
                        "323031332032303a31333a323220474d" +
                        "54c05a04677a69707738666f6f3d4153" +
                        "444a4b48514b425a584f5157454f5049" +
                        "5541585157454f49553b206d61782d61" +
                        "67653d333630303b2076657273696f6e" +
                        "3d31" +
                        // Header list end
                        "00");
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 1, buffer.capacity() - 1);
        assertEquals(99, fw.limit());

        Map<String, String> headers = new LinkedHashMap<>();
        fw.forEach(getHeaders(context, headers));

        assertEquals(6, headers.size());
        assertEquals("200", headers.get(":status"));
        assertEquals("private", headers.get("cache-control"));
        assertEquals("Mon, 21 Oct 2013 20:13:22 GMT", headers.get("date"));
        assertEquals("https://www.example.com", headers.get("location"));
        assertEquals("gzip", headers.get("content-encoding"));
        assertEquals("foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1", headers.get("set-cookie"));

        assertEquals(3, context.table.size());
        assertEquals(215, context.tableSize);
        assertEquals("set-cookie", context.name(62));
        assertEquals("foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1", context.value(62));
        assertEquals("content-encoding", context.name(63));
        assertEquals("gzip", context.value(63));
        assertEquals("date", context.name(64));
        assertEquals("Mon, 21 Oct 2013 20:13:22 GMT", context.value(64));
    }

    @Test
    public void error()
    {
        byte[] bytes = BitUtil.fromHex(
                        // Header list begin
                        "828684418aa0e41d139d09b8f01e07" +
                        "0085f2b24a87fffffffd25427f"
                        // Header list end
                        );
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        HpackHeaderBlockFW fw = new HpackHeaderBlockFW().wrap(buffer, 0, buffer.capacity() - 1);
        assertTrue(fw.error());
        assertEquals(27, fw.limit());
    }

    public static Consumer<HpackHeaderFieldFW> getHeaders(HpackContext context, Map<String, String> headers)
    {
        return x ->
        {
            HeaderFieldType headerFieldType = x.type();
            String name = null;
            String value = null;
            switch (headerFieldType)
            {
            case INDEXED :
                int index = x.index();
                name = context.name(index);
                value = context.value(index);
                headers.put(name, value);
                break;
            case LITERAL :
                HpackLiteralHeaderFieldFW literalRO = x.literal();
                switch (literalRO.nameType())
                {
                case INDEXED:
                    index = literalRO.nameIndex();
                    name = context.name(index);
                    value = string(literalRO.valueLiteral());
                    headers.put(name, value);
                    break;
                case NEW:
                    name = string(literalRO.nameLiteral());
                    value = string(literalRO.valueLiteral());
                    headers.put(name, value);
                    break;
                }
                if (literalRO.literalType() == INCREMENTAL_INDEXING)
                {
                    context.add(name, value);
                }
                break;

            case UPDATE:
                break;

            case UNKNOWN:
                throw new IllegalStateException();
            }
        };
    }

    private static String string(HpackStringFW value)
    {
        DirectBuffer valuePayload = value.payload();
        if (value.huffman())
        {
            MutableDirectBuffer dst = new UnsafeBuffer(new byte[4096]);
            int length = HpackHuffman.decode(valuePayload, dst);
            assert length != -1;
            return dst.getStringWithoutLengthUtf8(0, length);
        }
        else
        {
            return valuePayload.getStringWithoutLengthUtf8(0, valuePayload.capacity());
        }
    }

}
