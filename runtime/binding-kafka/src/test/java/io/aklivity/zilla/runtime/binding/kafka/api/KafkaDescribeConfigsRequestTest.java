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
import java.util.function.Consumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest.Generator;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest.Resource;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest.Source.ResourceConsumer;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDescribeConfigsRequestTest
{
    // body bytes only, as verified against the DescribeConfigs v4 wire encoder output (RequestHeader
    // apiKey/apiVersion/correlationId/clientId are encoded separately and excluded here)
    private static final byte[] EXPECTED = new byte[]
    {
        0x00,
        0x02,
        0x02,
        0x07, 'e', 'v', 'e', 'n', 't', 's',
        0x00,
        0x00,
        0x00,
        0x00,
        0x00
    };

    @Test
    public void shouldGenerateDescribeConfigsV4RequestForAllConfigs()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.resources(1);

        Resource events = generator.resource()
            .type(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC)
            .name("events")
            .configNames(KafkaDescribeConfigsRequest.ALL_CONFIGS);
        assertTrue(events.build());

        assertTrue(generator.build(false, false));

        int limit = generator.limit();
        assertEquals(EXPECTED.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED, actual);
    }

    @Test
    public void shouldGenerateDescribeConfigsV4RequestWithExplicitConfigNames()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.resources(1);

        Resource resource = generator.resource()
            .type(KafkaDescribeConfigsRequest.RESOURCE_TYPE_BROKER)
            .name("0")
            .configNames(2);
        resource.configName("cleanup.policy");
        resource.configName("retention.ms");
        assertTrue(resource.build());

        assertTrue(generator.build(false, false));

        FakeSource source = new FakeSource(
            List.of(new FakeResource(KafkaDescribeConfigsRequest.RESOURCE_TYPE_BROKER, "0",
                List.of("cleanup.policy", "retention.ms"))));
        assertEquals(generator.limit(), KafkaDescribeConfigsRequest.sizeof(source, (short) 4));
    }

    @Test
    public void shouldRejectConfigNameCountMismatch()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.resources(1);

        Resource resource = generator.resource()
            .type(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC)
            .name("events")
            .configNames(2);
        resource.configName("cleanup.policy");

        assertFalse(resource.build());
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.resources(1);

        Resource resource = generator.resource()
            .type(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC)
            .name("events-resource-name-too-long-to-fit")
            .configNames(KafkaDescribeConfigsRequest.ALL_CONFIGS);

        assertFalse(resource.build());
    }

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource(
            List.of(new FakeResource(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC, "events", List.of())));

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
            List.of(new FakeResource(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC, "events", List.of())));

        assertEquals(EXPECTED.length, KafkaDescribeConfigsRequest.sizeof(source, (short) 4));
    }

    @Test
    public void shouldComputeSizeofForMultiByteUtf8Names()
    {
        FakeSource source = new FakeSource(
            List.of(new FakeResource(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC, "café", List.of("日本", "🎉"))));

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaDescribeConfigsRequest.sizeof(source, (short) 4));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        FakeSource source = new FakeSource(List.of());

        assertThrows(UnsupportedOperationException.class, () -> KafkaDescribeConfigsRequest.sizeof(source, (short) 1));
    }

    private record FakeResource(
        byte type,
        String name,
        List<String> configNames) implements Source.Resource
    {
        @Override
        public int configCount()
        {
            return configNames.isEmpty() ? KafkaDescribeConfigsRequest.ALL_CONFIGS : configNames.size();
        }

        @Override
        public void forEachConfigName(
            Consumer<String> consumer)
        {
            configNames.forEach(consumer);
        }
    }

    private record FakeSource(
        List<FakeResource> resources) implements Source
    {
        @Override
        public int resourceCount()
        {
            return resources.size();
        }

        @Override
        public void forEach(
            ResourceConsumer consumer)
        {
            resources.forEach(consumer::accept);
        }
    }
}
