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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteTopicsResponse.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDeleteTopicsResponseV6FWTest
{
    // body bytes only, as verified against the real KafkaClientDeleteTopicsFactory v6 wire decoder input
    // (the response header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x03,
        0x07, 'e', 'v', 'e', 'n', 't', 's',
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x00,
        0x00,
        0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x03,
        0x10, 't', 'o', 'p', 'i', 'c', ' ', 'n', 'o', 't', ' ', 'f', 'o', 'u', 'n', 'd',
        0x00,
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
    public void shouldDecodeDeleteTopicsV6Response()
    {
        KafkaDeleteTopicsResponseV6FW response = new KafkaDeleteTopicsResponseV6FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(2, response.topicCount());

        assertTrue(response.hasNext());
        Topic events = response.next();
        assertEquals("events", asString(events.buffer(), events.nameOffset(), events.nameLength()));
        assertEquals(0L, events.topicIdMostSigBits());
        assertEquals(0L, events.topicIdLeastSigBits());
        assertEquals(0, events.error());
        assertEquals(-1, events.messageLength());

        assertTrue(response.hasNext());
        Topic missing = response.next();
        assertEquals(-1, missing.nameLength());
        assertEquals(0L, missing.topicIdMostSigBits());
        assertEquals(0L, missing.topicIdLeastSigBits());
        assertEquals(3, missing.error());
        assertEquals("topic not found", asString(missing.buffer(), missing.messageOffset(), missing.messageLength()));

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
