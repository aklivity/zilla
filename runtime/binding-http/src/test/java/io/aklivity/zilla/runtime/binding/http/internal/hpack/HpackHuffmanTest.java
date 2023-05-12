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
package io.aklivity.zilla.runtime.binding.http.internal.hpack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class HpackHuffmanTest
{

    // Tests decoding of examples from RFC 7541 (HPACK)
    @Test
    public void decode()
    {
        decode("f1e3c2e5f23a6ba0ab90f4ff", "www.example.com");
        decode("a8eb10649cbf", "no-cache");
        decode("25a849e95ba97d7f", "custom-key");
        decode("25a849e95bb8e8b4bf", "custom-value");
        decode("6402", "302");
        decode("aec3771a4b", "private");
        decode("d07abe941054d444a8200595040b8166e082a62d1bff", "Mon, 21 Oct 2013 20:13:21 GMT");
        decode("9d29ad171863c78f0b97c8e9ae82ae43d3", "https://www.example.com");
        decode("640eff", "307");
        decode("d07abe941054d444a8200595040b8166e084a62d1bff", "Mon, 21 Oct 2013 20:13:22 GMT");
        decode("9bd9ab", "gzip");
        decode("94e7821dd7f2e6c7b335dfdfcd5b3960d5af27087f3672c1ab270fb5291f9587316065c003ed4ee5b1063d5007",
                "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1");
    }

    @Test
    public void decodeReject()
    {
        byte[] bytes = BitUtil.fromHex("006402");
        DirectBuffer buf = new UnsafeBuffer(bytes, 1, bytes.length - 1);
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[0]);
        int length = HpackHuffman.decode(buf, dst);
        assertEquals(-1, length);
    }

    private void decode(String encoded, String expected)
    {
        byte[] bytes = BitUtil.fromHex("00" + encoded);    // +00 to test offset
        DirectBuffer buf = new UnsafeBuffer(bytes, 1, bytes.length - 1);
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[4096]);
        int length = HpackHuffman.decode(buf, dst);
        assertNotEquals(-1, length);
        String got = dst.getStringWithoutLengthUtf8(0, length);
        assertEquals(expected, got);
    }

    // Tests encoding of examples from RFC 7541 (HPACK)
    @Test
    public void encode()
    {
        encode("www.example.com", "f1e3c2e5f23a6ba0ab90f4ff");
        encode("no-cache", "a8eb10649cbf");
        encode("custom-key", "25a849e95ba97d7f");
        encode("custom-value", "25a849e95bb8e8b4bf");
        encode("302", "6402");
        encode("private", "aec3771a4b");
        encode("Mon, 21 Oct 2013 20:13:21 GMT", "d07abe941054d444a8200595040b8166e082a62d1bff");
        encode("https://www.example.com", "9d29ad171863c78f0b97c8e9ae82ae43d3");
        encode("307", "640eff");
        encode("Mon, 21 Oct 2013 20:13:22 GMT", "d07abe941054d444a8200595040b8166e084a62d1bff");
        encode("gzip", "9bd9ab");
        encode("foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1",
                "94e7821dd7f2e6c7b335dfdfcd5b3960d5af27087f3672c1ab270fb5291f9587316065c003ed4ee5b1063d5007");
    }

    private void encode(String str, String expected)
    {
        byte[] expectedBytes = BitUtil.fromHex(expected);
        DirectBuffer expectedBuf = new UnsafeBuffer(expectedBytes);

        byte[] bytes = str.getBytes(UTF_8);
        DirectBuffer buf = new UnsafeBuffer(bytes, 0, bytes.length);

        int size = HpackHuffman.encodedSize(buf, 0, buf.capacity());
        MutableDirectBuffer encodedBuf = new UnsafeBuffer(new byte[size]);
        HpackHuffman.encode(buf, encodedBuf);
        assertEquals(expectedBuf, encodedBuf);
    }
}
