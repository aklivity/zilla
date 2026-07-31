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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest.Assignment;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest.Generator;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest.Source.AssignmentConsumer;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest.Source.ConfigConsumer;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest.Source.TopicConsumer;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCreateTopicsRequestTest
{
    // body bytes only, as verified against the real KafkaClientCreateTopicsFactory v7 wire encoder output
    // (RequestHeader apiKey/apiVersion/correlationId/clientId are encoded separately and excluded here)
    private static final byte[] EXPECTED = new byte[]
    {
        0x00, 0x03,
        0x07, 'e', 'v', 'e', 'n', 't', 's',
        0x00, 0x00, 0x00, 0x01,
        0x00, 0x01,
        0x02,
        0x00, 0x00, 0x00, 0x00,
        0x02,
        0x00, 0x00, 0x00, 0x00,
        0x00,
        0x02,
        0x0f, 'c', 'l', 'e', 'a', 'n', 'u', 'p', '.', 'p', 'o', 'l', 'i', 'c', 'y',
        0x07, 'd', 'e', 'l', 'e', 't', 'e',
        0x00,
        0x00,
        0x0a, 's', 'n', 'a', 'p', 's', 'h', 'o', 't', 's',
        0x00, 0x00, 0x00, 0x01,
        0x00, 0x01,
        0x02,
        0x00, 0x00, 0x00, 0x00,
        0x02,
        0x00, 0x00, 0x00, 0x00,
        0x00,
        0x02,
        0x0f, 'c', 'l', 'e', 'a', 'n', 'u', 'p', '.', 'p', 'o', 'l', 'i', 'c', 'y',
        0x08, 'c', 'o', 'm', 'p', 'a', 'c', 't',
        0x00,
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00,
        0x00
    };

    @Test
    public void shouldGenerateCreateTopicsV7Request()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(2);

        Topic events = generator.topic()
            .name("events")
            .partitions(1)
            .replicas((short) 1)
            .assignments(1);
        Assignment eventsAssignment = events.assignment()
            .partitionIndex(0)
            .brokers(1)
            .broker(0);
        events = eventsAssignment.build();
        events.configs(1);
        events = events.config()
            .name("cleanup.policy")
            .value("delete")
            .build();
        assertTrue(events.build());

        Topic snapshots = generator.topic()
            .name("snapshots")
            .partitions(1)
            .replicas((short) 1)
            .assignments(1);
        Assignment snapshotsAssignment = snapshots.assignment()
            .partitionIndex(0)
            .brokers(1)
            .broker(0);
        snapshots = snapshotsAssignment.build();
        snapshots.configs(1);
        snapshots = snapshots.config()
            .name("cleanup.policy")
            .value("compact")
            .build();
        assertTrue(snapshots.build());

        assertTrue(generator.build(0, false));

        int limit = generator.limit();
        assertEquals(EXPECTED.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED, actual);
    }

    @Test
    public void shouldGenerateTopicWithNoAssignmentsOrConfigs()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(1);

        Topic topic = generator.topic()
            .name("events")
            .partitions(1)
            .replicas((short) 1);

        assertTrue(topic.build());
        assertTrue(generator.build(0, false));
    }

    @Test
    public void shouldRejectAssignmentCountMismatch()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(1);

        Topic topic = generator.topic()
            .name("events")
            .partitions(1)
            .replicas((short) 1)
            .assignments(2);

        Assignment assignment = topic.assignment()
            .partitionIndex(0)
            .brokers(1)
            .broker(0);
        topic = assignment.build();

        assertFalse(topic.build());
    }

    @Test
    public void shouldRejectBrokerCountMismatch()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(1);

        Topic topic = generator.topic()
            .name("events")
            .partitions(1)
            .replicas((short) 1)
            .assignments(1);

        Assignment assignment = topic.assignment()
            .partitionIndex(0)
            .brokers(2);
        assignment.broker(0);
        topic = assignment.build();

        assertFalse(topic.build());
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(1);

        Topic topic = generator.topic()
            .name("events-topic-name-too-long-to-fit")
            .partitions(1)
            .replicas((short) 1)
            .assignments(0);

        assertFalse(topic.build());
    }

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource(
            List.of(
                new FakeTopic("events", 1, (short) 1,
                    List.of(new FakeAssignment(0, List.of(0))),
                    List.of(new FakeConfig("cleanup.policy", "delete"))),
                new FakeTopic("snapshots", 1, (short) 1,
                    List.of(new FakeAssignment(0, List.of(0))),
                    List.of(new FakeConfig("cleanup.policy", "compact")))),
            0,
            false);

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
        FakeSource source = new FakeSource(
            List.of(
                new FakeTopic("events", 1, (short) 1,
                    List.of(new FakeAssignment(0, List.of(0))),
                    List.of(new FakeConfig("cleanup.policy", "delete"))),
                new FakeTopic("snapshots", 1, (short) 1,
                    List.of(new FakeAssignment(0, List.of(0))),
                    List.of(new FakeConfig("cleanup.policy", "compact")))),
            0,
            false);

        assertEquals(EXPECTED.length, KafkaCreateTopicsRequest.sizeof(source, (short) 7));
    }

    @Test
    public void shouldComputeSizeofForNullConfigValue()
    {
        FakeSource source = new FakeSource(
            List.of(
                new FakeTopic("events", 1, (short) 1,
                    List.of(),
                    List.of(new FakeConfig("cleanup.policy", null)))),
            0,
            false);

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaCreateTopicsRequest.sizeof(source, (short) 7));
    }

    @Test
    public void shouldComputeSizeofForMultiByteUtf8Names()
    {
        // "café" - trailing e-acute is a 2-byte UTF-8 sequence (U+00E9)
        // "日本" - two 3-byte UTF-8 sequences (U+65E5, U+672C)
        // "🎉" - a surrogate pair encoding one 4-byte UTF-8 code point (U+1F389)
        FakeSource source = new FakeSource(
            List.of(
                new FakeTopic("café", 1, (short) 1,
                    List.of(),
                    List.of(new FakeConfig("日本", "🎉"))),
                new FakeTopic("🎉-日本-café", 1, (short) 1,
                    List.of(), List.of())),
            0,
            false);

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaCreateTopicsRequest.sizeof(source, (short) 7));
    }

    @Test
    public void shouldComputeSizeofAcrossVarintWidthBoundary()
    {
        // a 127-byte name pushes the compact-string length prefix from 1 byte (N+1 <= 127) to 2 bytes
        String longName = "a".repeat(127);
        FakeSource source = new FakeSource(
            List.of(new FakeTopic(longName, 1, (short) 1, List.of(), List.of())),
            0,
            false);

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[512]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaCreateTopicsRequest.sizeof(source, (short) 7));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        FakeSource source = new FakeSource(List.of(), 0, false);

        assertThrows(UnsupportedOperationException.class, () -> KafkaCreateTopicsRequest.sizeof(source, (short) 3));
    }

    private record FakeConfig(
        String name,
        String value) implements Source.Config
    {
    }

    private record FakeAssignment(
        int partitionIndex,
        List<Integer> brokerIds) implements Source.Assignment
    {
        @Override
        public int brokerCount()
        {
            return brokerIds.size();
        }

        @Override
        public void forEachBroker(
            IntConsumer consumer)
        {
            brokerIds.forEach(consumer::accept);
        }
    }

    private record FakeTopic(
        String name,
        int partitions,
        short replicas,
        List<FakeAssignment> assignments,
        List<FakeConfig> configs) implements Source.Topic
    {
        @Override
        public int assignmentCount()
        {
            return assignments.size();
        }

        @Override
        public void forEachAssignment(
            AssignmentConsumer consumer)
        {
            assignments.forEach(consumer::accept);
        }

        @Override
        public int configCount()
        {
            return configs.size();
        }

        @Override
        public void forEachConfig(
            ConfigConsumer consumer)
        {
            configs.forEach(consumer::accept);
        }
    }

    private record FakeSource(
        List<FakeTopic> topics,
        int timeoutMs,
        boolean validateOnly) implements Source
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
