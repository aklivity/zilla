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
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class FindCoordinatorResponseV3FWTest
{
    // body bytes only, as verified against the real Kafka FindCoordinator v3 wire decoder input
    // (the response header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x00,
        0x00, 0x00, 0x00, 0x01,
        0x08, 'b', 'r', 'o', 'k', 'e', 'r', '1',
        0x00, 0x00, 0x23, (byte) 0x84,
        0x00
    };

    private static String asString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return length == -1 ? null : buffer.getStringWithoutLengthUtf8(offset, length);
    }

    @Test
    public void shouldDecodeFindCoordinatorV3Response()
    {
        FindCoordinatorResponseV3FW response = new FindCoordinatorResponseV3FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(0, response.error());
        assertNull(asString(response.buffer(), response.messageOffset(), response.messageLength()));
        assertEquals(1, response.nodeId());
        assertEquals("broker1", asString(response.buffer(), response.hostOffset(), response.hostLength()));
        assertEquals(9092, response.port());
        assertEquals(BODY.length, response.limit());
    }
}
