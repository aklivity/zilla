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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListOffsetsResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListOffsetsResponse.Partition;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListOffsetsResponse.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaListOffsetsResponseV6FWTest
{
    // body bytes only, as verified against the real Kafka ListOffsets v6 wire decoder input
    // (the response header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x02,
        0x09, 'm', 'y', '-', 't', 'o', 'p', 'i', 'c',
        0x02,
        0x00, 0x00, 0x00, 0x00, // partitionIndex = 0
        0x00, 0x00, // errorCode = 0
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, // timestamp = -1
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x64, // endOffset = 100
        0x00, 0x00, 0x00, 0x05, // leaderEpoch = 5
        0x00, // partition tagged fields
        0x00, // topic tagged fields
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
    public void shouldDecodeListOffsetsV6Response()
    {
        KafkaListOffsetsResponseV6FW response = new KafkaListOffsetsResponseV6FW();

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
        assertEquals(0, partition.errorCode());
        assertEquals(-1L, partition.timestamp());
        assertEquals(100L, partition.endOffset());
        assertEquals(5, partition.leaderEpoch());

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
