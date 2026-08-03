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
package io.aklivity.zilla.runtime.binding.kafka.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsResponse.Result;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCreateAclsResponseV2FWTest
{
    // body bytes only, as verified against the CreateAcls v2 wire decoder input (the response
    // header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,                                                                // tagged fields (header)
        0x00, 0x00, 0x00, 0x00,                                              // throttle time ms
        0x03,                                                                // result count (2)
        0x00, 0x00,                                                          // error (ok)
        0x00,                                                                // message (null)
        0x00,                                                                // tagged fields
        0x00, 0x3a,                                                          // error (58, security disabled)
        0x12, 's', 'e', 'c', 'u', 'r', 'i', 't', 'y', ' ', 'd', 'i', 's', 'a', 'b', 'l', 'e', 'd', // message
        0x00,                                                                // tagged fields
        0x00                                                                 // tagged fields (top)
    };

    private static String asString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return length == -1 ? null : buffer.getStringWithoutLengthUtf8(offset, length);
    }

    @Test
    public void shouldDecodeCreateAclsV2Response()
    {
        KafkaCreateAclsResponseV2FW response = new KafkaCreateAclsResponseV2FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(2, response.resultCount());

        assertTrue(response.hasNext());
        Result ok = response.next();
        assertEquals(0, ok.error());
        assertEquals(-1, ok.messageLength());

        assertTrue(response.hasNext());
        Result failed = response.next();
        assertEquals(58, failed.error());
        assertEquals("security disabled", asString(failed.buffer(), failed.messageOffset(), failed.messageLength()));

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
