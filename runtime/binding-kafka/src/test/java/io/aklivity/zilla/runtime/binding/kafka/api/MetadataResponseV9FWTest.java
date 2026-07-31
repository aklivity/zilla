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

import java.util.PrimitiveIterator;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.MetadataResponse.Broker;
import io.aklivity.zilla.runtime.binding.kafka.api.MetadataResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.MetadataResponse.Partition;
import io.aklivity.zilla.runtime.binding.kafka.api.MetadataResponse.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class MetadataResponseV9FWTest
{
    // body bytes only, as verified against the official Metadata v9 response schema
    // (the response header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x02,
        0x00, 0x00, 0x00, 0x01,
        0x0a, 'l', 'o', 'c', 'a', 'l', 'h', 'o', 's', 't',
        0x00, 0x00, 0x23, (byte) 0x84,
        0x00,
        0x00,
        0x0d, 't', 'e', 's', 't', '-', 'c', 'l', 'u', 's', 't', 'e', 'r',
        0x00, 0x00, 0x00, 0x01,
        0x02,
        0x00, 0x00,
        0x07, 'e', 'v', 'e', 'n', 't', 's',
        0x00,
        0x02,
        0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00,
        0x02,
        0x00, 0x00, 0x00, 0x01,
        0x02,
        0x00, 0x00, 0x00, 0x01,
        0x01,
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00,
        0x00, 0x00, 0x00, 0x00,
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
    public void shouldDecodeMetadataV9Response()
    {
        MetadataResponseV9FW response = new MetadataResponseV9FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(1, response.brokerCount());

        assertTrue(response.hasNextBroker());
        Broker broker = response.nextBroker();
        assertEquals(1, broker.nodeId());
        assertEquals("localhost", asString(response.buffer(), broker.hostOffset(), broker.hostLength()));
        assertEquals(9092, broker.port());
        assertEquals(-1, broker.rackLength());

        assertFalse(response.hasNextBroker());

        assertEquals("test-cluster", asString(response.buffer(), response.clusterIdOffset(), response.clusterIdLength()));
        assertEquals(1, response.controllerId());
        assertEquals(1, response.topicCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.TOPIC, response.next());

        Topic topic = response.topic();
        assertEquals("events", asString(response.buffer(), topic.nameOffset(), topic.nameLength()));
        assertEquals(0, topic.error());
        assertFalse(topic.isInternal());
        assertEquals(1, topic.partitionCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.PARTITION, response.next());

        Partition partition = response.partition();
        assertEquals(0, partition.error());
        assertEquals(0, partition.partitionId());
        assertEquals(1, partition.leader());
        assertEquals(0, partition.leaderEpoch());

        assertEquals(1, partition.replicaCount());
        PrimitiveIterator.OfInt replicas = partition.replicas();
        assertTrue(replicas.hasNext());
        assertEquals(1, replicas.nextInt());
        assertFalse(replicas.hasNext());

        assertEquals(1, partition.isrCount());
        PrimitiveIterator.OfInt isr = partition.isr();
        assertTrue(isr.hasNext());
        assertEquals(1, isr.nextInt());
        assertFalse(isr.hasNext());

        assertEquals(0, partition.offlineReplicaCount());

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
