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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaMetadataRequest.Generator;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaMetadataRequest.Source;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaMetadataRequestTest
{
    // body bytes only, as verified against the official Metadata v9 request schema
    // (RequestHeader apiKey/apiVersion/correlationId/clientId are encoded separately and excluded here)
    private static final byte[] EXPECTED_TWO_TOPICS = new byte[]
    {
        0x00,
        0x03,
        0x07, 'e', 'v', 'e', 'n', 't', 's',
        0x00,
        0x0a, 's', 'n', 'a', 'p', 's', 'h', 'o', 't', 's',
        0x00,
        0x00, 0x00, 0x00,
        0x00
    };

    // a null (rather than empty) topics array requests every topic in the cluster
    private static final byte[] EXPECTED_ALL_TOPICS = new byte[]
    {
        0x00,
        0x00,
        0x00, 0x00, 0x00,
        0x00
    };

    @Test
    public void shouldGenerateMetadataV9RequestForNamedTopics()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(2);
        generator.topic("events");
        generator.topic("snapshots");

        assertTrue(generator.build());

        int limit = generator.limit();
        assertEquals(EXPECTED_TWO_TOPICS.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED_TWO_TOPICS, actual);
    }

    @Test
    public void shouldGenerateMetadataV9RequestForAllTopics()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(-1);

        assertTrue(generator.build());

        int limit = generator.limit();
        assertEquals(EXPECTED_ALL_TOPICS.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED_ALL_TOPICS, actual);
    }

    @Test
    public void shouldRejectTopicCountMismatch()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(2);
        generator.topic("events");

        assertFalse(generator.build());
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.topics(1);
        generator.topic("events-topic-name-too-long-to-fit");

        assertFalse(generator.build());
    }

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytesForNamedTopics()
    {
        FakeSource source = new FakeSource(false, List.of("events", "snapshots"));

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));

        int limit = generator.limit();
        assertEquals(EXPECTED_TWO_TOPICS.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED_TWO_TOPICS, actual);
    }

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytesForAllTopics()
    {
        FakeSource source = new FakeSource(true, List.of());

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));

        int limit = generator.limit();
        assertEquals(EXPECTED_ALL_TOPICS.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED_ALL_TOPICS, actual);
    }

    @Test
    public void shouldComputeSizeofMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource(false, List.of("events", "snapshots"));

        assertEquals(EXPECTED_TWO_TOPICS.length, KafkaMetadataRequest.sizeof(source, (short) 9));
    }

    @Test
    public void shouldComputeSizeofForAllTopics()
    {
        FakeSource source = new FakeSource(true, List.of());

        assertEquals(EXPECTED_ALL_TOPICS.length, KafkaMetadataRequest.sizeof(source, (short) 9));
    }

    @Test
    public void shouldComputeSizeofForMultiByteUtf8Names()
    {
        // "café" - trailing e-acute is a 2-byte UTF-8 sequence (U+00E9)
        // "日本" - two 3-byte UTF-8 sequences (U+65E5, U+672C)
        // "🎉-topic" - a surrogate pair encoding one 4-byte UTF-8 code point (U+1F389)
        FakeSource source = new FakeSource(false, List.of("café", "日本", "🎉-topic"));

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaMetadataRequest.sizeof(source, (short) 9));
    }

    @Test
    public void shouldComputeSizeofAcrossVarintWidthBoundary()
    {
        // 128 topics pushes the compact-array count prefix from 1 byte (N+1 <= 127) to 2 bytes
        List<String> topics = new ArrayList<>();
        for (int i = 0; i < 128; i++)
        {
            topics.add("t" + i);
        }
        FakeSource source = new FakeSource(false, topics);

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4096]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaMetadataRequest.sizeof(source, (short) 9));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        FakeSource source = new FakeSource(false, List.of());

        assertThrows(UnsupportedOperationException.class, () -> KafkaMetadataRequest.sizeof(source, (short) 5));
    }

    private record FakeSource(
        boolean allTopics,
        List<String> topics) implements Source
    {
        @Override
        public int topicCount()
        {
            return topics.size();
        }

        @Override
        public void forEach(
            Consumer<String> consumer)
        {
            topics.forEach(consumer);
        }
    }
}
