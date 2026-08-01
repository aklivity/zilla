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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetFetchResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetFetchResponse.Partition;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetFetchResponse.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaOffsetFetchResponseV6FWTest
{
    // body bytes only, as verified against the real Kafka OffsetFetch v6 wire decoder input
    // (the response header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x02,
        0x09, 'm', 'y', '-', 't', 'o', 'p', 'i', 'c',
        0x02,
        0x00, 0x00, 0x00, 0x00, // partitionIndex = 0
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2a, // committedOffset = 42
        0x00, 0x00, 0x00, 0x03, // committedLeaderEpoch = 3
        0x00, // metadata = null
        0x00, 0x00, // errorCode = 0
        0x00, // partition tagged fields
        0x00, // topic tagged fields
        0x00, 0x00, // group-level error code = 0
        0x00 // response tagged fields
    };

    private static String asString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return length == -1 ? null : buffer.getStringWithoutLengthUtf8(offset, length);
    }

    @Test
    public void shouldDecodeOffsetFetchV6Response()
    {
        KafkaOffsetFetchResponseV6FW response = new KafkaOffsetFetchResponseV6FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(1, response.topicCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.TOPIC, response.next());

        Topic topic = response.topic();
        assertEquals("my-topic", asString(topic.buffer(), topic.nameOffset(), topic.nameLength()));
        assertEquals(1, topic.partitionCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.PARTITION, response.next());

        Partition partition = response.partition();
        assertEquals(0, partition.partitionIndex());
        assertEquals(42L, partition.committedOffset());
        assertEquals(3, partition.committedLeaderEpoch());
        assertNull(asString(partition.buffer(), partition.metadataOffset(), partition.metadataLength()));
        assertEquals(0, partition.errorCode());

        assertFalse(response.hasNext());
        assertEquals(0, response.error());
        assertEquals(BODY.length, response.limit());
    }
}
