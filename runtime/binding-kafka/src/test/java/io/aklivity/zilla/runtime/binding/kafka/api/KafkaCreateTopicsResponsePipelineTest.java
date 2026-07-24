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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsResponsePipeline.ConfigResult;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsResponsePipeline.Response;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsResponsePipeline.TopicResult;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCreateTopicsResponsePipelineTest
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

    @Test
    public void shouldDecodeCreateTopicsV7Response()
    {
        KafkaCreateTopicsResponsePipeline pipeline = new KafkaCreateTopicsResponsePipeline();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        Response response = pipeline.decode(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(2, response.topics().size());

        TopicResult events = response.topics().get(0);
        assertEquals("events", events.name());
        assertEquals(0L, events.topicIdMostSigBits());
        assertEquals(0L, events.topicIdLeastSigBits());
        assertEquals(0, events.error());
        assertNull(events.message());
        assertEquals(1, events.numPartitions());
        assertEquals(1, events.replicationFactor());
        assertEquals(1, events.configs().size());

        ConfigResult cleanupPolicy = events.configs().get(0);
        assertEquals("cleanup.policy", cleanupPolicy.name());
        assertEquals("delete", cleanupPolicy.value());
        assertEquals(false, cleanupPolicy.readOnly());
        assertEquals(1, cleanupPolicy.configSource());
        assertEquals(false, cleanupPolicy.isSensitive());

        TopicResult snapshots = response.topics().get(1);
        assertEquals("snapshots", snapshots.name());
        assertEquals(1, snapshots.numPartitions());
        assertEquals(1, snapshots.replicationFactor());
        assertTrue(snapshots.configs().isEmpty());
    }
}
