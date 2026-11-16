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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.function.IntConsumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetDeleteRequest.Generator;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetDeleteRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetDeleteRequest.Source.TopicConsumer;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetDeleteRequest.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaOffsetDeleteRequestTest
{
    // body bytes only, laid out per the Kafka OffsetDelete v0 request schema (non-flexible: int16-prefixed
    // strings, int32 array counts, no tagged fields; request header v1 is encoded separately and excluded here)
    private static final byte[] EXPECTED = new byte[]
    {
        0x00, 0x08, 'm', 'y', '-', 'g', 'r', 'o', 'u', 'p', // groupId
        0x00, 0x00, 0x00, 0x01, // topicCount = 1
        0x00, 0x08, 'm', 'y', '-', 't', 'o', 'p', 'i', 'c', // topic name
        0x00, 0x00, 0x00, 0x02, // partitionCount = 2
        0x00, 0x00, 0x00, 0x00, // partitionIndex = 0
        0x00, 0x00, 0x00, 0x01 // partitionIndex = 1
    };

    @Test
    public void shouldGenerateOffsetDeleteV0Request()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics("my-group", 1);

        Topic topic = generator.topic()
            .name("my-topic")
            .partitions(2);
        topic.partition(0);
        topic.partition(1);
        assertTrue(topic.build());

        assertTrue(generator.build());

        int limit = generator.limit();
        assertEquals(EXPECTED.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED, actual);
    }

    @Test
    public void shouldRejectPartitionCountMismatch()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics("my-group", 1);

        Topic topic = generator.topic()
            .name("my-topic")
            .partitions(2);
        topic.partition(0);

        assertFalse(topic.build());
    }

    @Test
    public void shouldRejectTopicCountMismatch()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics("my-group", 2);

        Topic topic = generator.topic()
            .name("my-topic")
            .partitions(0);
        assertTrue(topic.build());

        assertFalse(generator.build());
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics("my-group", 1);

        Topic topic = generator.topic()
            .name("my-topic-name-too-long-to-fit")
            .partitions(0);

        assertFalse(topic.build());
    }

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource("my-group", List.of(new FakeTopic("my-topic", List.of(0, 1))));

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));

        int limit = generator.limit();
        assertEquals(EXPECTED.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED, actual);
    }

    @Test
    public void shouldComputeSizeofMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource("my-group", List.of(new FakeTopic("my-topic", List.of(0, 1))));

        assertEquals(EXPECTED.length, KafkaOffsetDeleteRequest.sizeof(source, (short) 0));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        FakeSource source = new FakeSource("my-group", List.of());

        assertThrows(UnsupportedOperationException.class, () -> KafkaOffsetDeleteRequest.sizeof(source, (short) 1));
    }

    private record FakeTopic(
        String name,
        List<Integer> partitions) implements Source.Topic
    {
        @Override
        public int partitionCount()
        {
            return partitions.size();
        }

        @Override
        public void forEachPartition(
            IntConsumer consumer)
        {
            partitions.forEach(consumer::accept);
        }
    }

    private record FakeSource(
        String groupId,
        List<FakeTopic> topics) implements Source
    {
        @Override
        public int topicCount()
        {
            return topics.size();
        }

        @Override
        public void forEach(
            TopicConsumer consumer)
        {
            topics.forEach(consumer::accept);
        }
    }
}
