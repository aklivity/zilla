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

import io.aklivity.zilla.runtime.binding.kafka.api.CreateTopicsResponse.Config;
import io.aklivity.zilla.runtime.binding.kafka.api.CreateTopicsResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.CreateTopicsResponse.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class CreateTopicsResponseV7FWTest
{
    // body bytes only, as verified against the real KafkaClientCreateTopicsFactory v7 wire decoder input
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
        0x00, 0x00, 0x00, 0x01,
        0x00, 0x01,
        0x02,
        0x0f, 'c', 'l', 'e', 'a', 'n', 'u', 'p', '.', 'p', 'o', 'l', 'i', 'c', 'y',
        0x07, 'd', 'e', 'l', 'e', 't', 'e',
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x0a, 's', 'n', 'a', 'p', 's', 'h', 'o', 't', 's',
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x00,
        0x00, 0x00, 0x00, 0x01,
        0x00, 0x01,
        0x01,
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
    public void shouldDecodeCreateTopicsV7Response()
    {
        CreateTopicsResponseV7FW response = new CreateTopicsResponseV7FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(2, response.topicCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.TOPIC, response.next());

        Topic events = response.topic();
        assertEquals("events", asString(events.buffer(), events.nameOffset(), events.nameLength()));
        assertEquals(0L, events.topicIdMostSigBits());
        assertEquals(0L, events.topicIdLeastSigBits());
        assertEquals(0, events.error());
        assertEquals(-1, events.messageLength());
        assertEquals(1, events.numPartitions());
        assertEquals(1, events.replicationFactor());
        assertEquals(1, events.configCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.CONFIG, response.next());

        Config cleanupPolicy = response.config();
        assertEquals("cleanup.policy", asString(cleanupPolicy.buffer(), cleanupPolicy.nameOffset(), cleanupPolicy.nameLength()));
        assertEquals("delete", asString(cleanupPolicy.buffer(), cleanupPolicy.valueOffset(), cleanupPolicy.valueLength()));
        assertFalse(cleanupPolicy.readOnly());
        assertEquals(1, cleanupPolicy.configSource());
        assertFalse(cleanupPolicy.isSensitive());

        assertTrue(response.hasNext());
        assertEquals(Kind.TOPIC, response.next());

        Topic snapshots = response.topic();
        assertEquals("snapshots", asString(snapshots.buffer(), snapshots.nameOffset(), snapshots.nameLength()));
        assertEquals(1, snapshots.numPartitions());
        assertEquals(1, snapshots.replicationFactor());
        assertEquals(0, snapshots.configCount());

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
