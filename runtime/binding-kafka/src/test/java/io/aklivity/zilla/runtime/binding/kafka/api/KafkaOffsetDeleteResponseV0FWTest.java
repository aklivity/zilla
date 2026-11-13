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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetDeleteResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetDeleteResponse.Partition;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetDeleteResponse.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaOffsetDeleteResponseV0FWTest
{
    // body bytes only, laid out per the Kafka OffsetDelete v0 response schema (non-flexible; response header v0,
    // the correlationId, is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00, 0x00, // top-level error = 0
        0x00, 0x00, 0x00, 0x00, // throttleTimeMillis = 0
        0x00, 0x00, 0x00, 0x01, // topicCount = 1
        0x00, 0x08, 'm', 'y', '-', 't', 'o', 'p', 'i', 'c',
        0x00, 0x00, 0x00, 0x02, // partitionCount = 2
        0x00, 0x00, 0x00, 0x00, // partitionIndex = 0
        0x00, 0x00, // error = 0
        0x00, 0x00, 0x00, 0x01, // partitionIndex = 1
        0x00, 0x56 // error = 86 GROUP_SUBSCRIBED_TO_TOPIC
    };

    // top-level error only: the broker reports no topics
    private static final byte[] GROUP_ERROR = new byte[]
    {
        0x00, 0x45, // top-level error = 69 GROUP_ID_NOT_FOUND
        0x00, 0x00, 0x00, 0x05, // throttleTimeMillis = 5
        0x00, 0x00, 0x00, 0x00 // topicCount = 0
    };

    // malformed: the topic count is negative
    private static final byte[] NEGATIVE_TOPIC_COUNT = new byte[]
    {
        0x00, 0x00, // top-level error = 0
        0x00, 0x00, 0x00, 0x00, // throttleTimeMillis = 0
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff // topicCount = -1
    };

    // malformed: the partition count of the topic is negative
    private static final byte[] NEGATIVE_PARTITION_COUNT = new byte[]
    {
        0x00, 0x00, // top-level error = 0
        0x00, 0x00, 0x00, 0x00, // throttleTimeMillis = 0
        0x00, 0x00, 0x00, 0x01, // topicCount = 1
        0x00, 0x08, 'm', 'y', '-', 't', 'o', 'p', 'i', 'c',
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff // partitionCount = -1
    };

    private static String asString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return buffer.getStringWithoutLengthUtf8(offset, length);
    }

    @Test
    public void shouldDecodeOffsetDeleteV0Response()
    {
        KafkaOffsetDeleteResponseV0FW response = new KafkaOffsetDeleteResponseV0FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.error());
        assertEquals(0, response.throttleTimeMillis());
        assertEquals(1, response.topicCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.TOPIC, response.next());

        Topic topic = response.topic();
        assertEquals("my-topic", asString(topic.buffer(), topic.nameOffset(), topic.nameLength()));
        assertEquals(2, topic.partitionCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.PARTITION, response.next());

        Partition partition = response.partition();
        assertEquals(0, partition.partitionIndex());
        assertEquals(0, partition.errorCode());

        assertTrue(response.hasNext());
        assertEquals(Kind.PARTITION, response.next());

        partition = response.partition();
        assertEquals(1, partition.partitionIndex());
        assertEquals(86, partition.errorCode());

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }

    @Test
    public void shouldDecodeTopLevelError()
    {
        KafkaOffsetDeleteResponseV0FW response = new KafkaOffsetDeleteResponseV0FW();

        DirectBufferEx buffer = new UnsafeBufferEx(GROUP_ERROR);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(69, response.error());
        assertEquals(5, response.throttleTimeMillis());
        assertEquals(0, response.topicCount());
        assertFalse(response.hasNext());
        assertEquals(GROUP_ERROR.length, response.limit());
    }

    @Test
    public void shouldRejectNegativeTopicCount()
    {
        KafkaOffsetDeleteResponseV0FW response = new KafkaOffsetDeleteResponseV0FW();

        DirectBufferEx buffer = new UnsafeBufferEx(NEGATIVE_TOPIC_COUNT);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(-1, response.topicCount());
        assertThrows(IllegalStateException.class, response::next);
    }

    @Test
    public void shouldRejectNegativePartitionCount()
    {
        KafkaOffsetDeleteResponseV0FW response = new KafkaOffsetDeleteResponseV0FW();

        DirectBufferEx buffer = new UnsafeBufferEx(NEGATIVE_PARTITION_COUNT);
        response.wrap(buffer, 0, buffer.capacity());

        assertTrue(response.hasNext());
        assertEquals(Kind.TOPIC, response.next());
        assertEquals(-1, response.topic().partitionCount());
        assertThrows(IllegalStateException.class, response::next);
    }
}
