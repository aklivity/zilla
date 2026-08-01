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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsResponse.Resource;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaAlterConfigsResponseV2FWTest
{
    // body bytes only, as verified against the AlterConfigs v2 wire decoder input (the response
    // header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,                                                                // tagged fields (header)
        0x00, 0x00, 0x00, 0x00,                                              // throttle time ms
        0x04,                                                                // resource count (3)
        0x00, 0x00,                                                          // error (events)
        0x00,                                                                // message (null)
        0x02,                                                                // type (topic)
        0x07, 'e', 'v', 'e', 'n', 't', 's',                                  // name
        0x00,                                                                // tagged fields
        0x00, 0x2a,                                                          // error (42, snapshots)
        0x11, 'p', 'o', 'l', 'i', 'c', 'y', ' ', 'v', 'i', 'o', 'l', 'a', 't', 'i', 'o', 'n', // message
        0x02,                                                                // type (topic)
        0x0a, 's', 'n', 'a', 'p', 's', 'h', 'o', 't', 's',                   // name
        0x00,                                                                // tagged fields
        0x00, 0x04,                                                          // error (4, broker)
        0x00,                                                                // message (null)
        0x04,                                                                // type (broker)
        0x02, '0',                                                           // name
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
    public void shouldDecodeAlterConfigsV2Response()
    {
        KafkaAlterConfigsResponseV2FW response = new KafkaAlterConfigsResponseV2FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(3, response.resourceCount());

        assertTrue(response.hasNext());
        Resource events = response.next();
        assertEquals(0, events.error());
        assertEquals(-1, events.messageLength());
        assertEquals(KafkaAlterConfigsRequest.RESOURCE_TYPE_TOPIC, events.type());
        assertEquals("events", asString(events.buffer(), events.nameOffset(), events.nameLength()));

        assertTrue(response.hasNext());
        Resource snapshots = response.next();
        assertEquals(42, snapshots.error());
        assertEquals("policy violation", asString(snapshots.buffer(), snapshots.messageOffset(), snapshots.messageLength()));
        assertEquals(KafkaAlterConfigsRequest.RESOURCE_TYPE_TOPIC, snapshots.type());
        assertEquals("snapshots", asString(snapshots.buffer(), snapshots.nameOffset(), snapshots.nameLength()));

        assertTrue(response.hasNext());
        Resource broker = response.next();
        assertEquals(4, broker.error());
        assertEquals(-1, broker.messageLength());
        assertEquals(KafkaAlterConfigsRequest.RESOURCE_TYPE_BROKER, broker.type());
        assertEquals("0", asString(broker.buffer(), broker.nameOffset(), broker.nameLength()));

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
