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

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest.Generator;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest.Resource;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest.Source.ConfigConsumer;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest.Source.ResourceConsumer;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaAlterConfigsRequestTest
{
    // body bytes only, as verified against KafkaClientAlterConfigsFactory-equivalent v2 wire encoder output
    // (RequestHeader apiKey/apiVersion/correlationId/clientId are encoded separately and excluded here)
    private static final byte[] EXPECTED = new byte[]
    {
        0x00,
        0x02,
        0x02,
        0x07, 'e', 'v', 'e', 'n', 't', 's',
        0x02,
        0x0f, 'c', 'l', 'e', 'a', 'n', 'u', 'p', '.', 'p', 'o', 'l', 'i', 'c', 'y',
        0x07, 'd', 'e', 'l', 'e', 't', 'e',
        0x00,
        0x00,
        0x00,
        0x00
    };

    @Test
    public void shouldGenerateAlterConfigsV2Request()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.resources(1);

        Resource events = generator.resource()
            .type(KafkaAlterConfigsRequest.RESOURCE_TYPE_TOPIC)
            .name("events")
            .configs(1);
        events = events.config()
            .name("cleanup.policy")
            .value("delete")
            .build();
        assertTrue(events.build());

        assertTrue(generator.build(false));

        int limit = generator.limit();
        assertEquals(EXPECTED.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED, actual);
    }

    @Test
    public void shouldRejectConfigCountMismatch()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.resources(1);

        Resource resource = generator.resource()
            .type(KafkaAlterConfigsRequest.RESOURCE_TYPE_TOPIC)
            .name("events")
            .configs(2);
        resource.config().name("cleanup.policy").value("delete").build();

        assertFalse(resource.build());
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.resources(1);

        Resource resource = generator.resource()
            .type(KafkaAlterConfigsRequest.RESOURCE_TYPE_TOPIC)
            .name("events-resource-name-too-long-to-fit")
            .configs(0);

        assertFalse(resource.build());
    }

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource(
            List.of(new FakeResource(KafkaAlterConfigsRequest.RESOURCE_TYPE_TOPIC, "events",
                List.of(new FakeConfig("cleanup.policy", "delete")))),
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
            List.of(new FakeResource(KafkaAlterConfigsRequest.RESOURCE_TYPE_TOPIC, "events",
                List.of(new FakeConfig("cleanup.policy", "delete")))),
            false);

        assertEquals(EXPECTED.length, KafkaAlterConfigsRequest.sizeof(source, (short) 2));
    }

    @Test
    public void shouldComputeSizeofForNullConfigValue()
    {
        FakeSource source = new FakeSource(
            List.of(new FakeResource(KafkaAlterConfigsRequest.RESOURCE_TYPE_BROKER, "0",
                List.of(new FakeConfig("log.retention.hours", null)))),
            true);

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaAlterConfigsRequest.sizeof(source, (short) 2));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        FakeSource source = new FakeSource(List.of(), false);

        assertThrows(UnsupportedOperationException.class, () -> KafkaAlterConfigsRequest.sizeof(source, (short) 1));
    }

    private record FakeConfig(
        String name,
        String value) implements Source.Config
    {
    }

    private record FakeResource(
        byte type,
        String name,
        List<FakeConfig> configs) implements Source.Resource
    {
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
        List<FakeResource> resources,
        boolean validateOnly) implements Source
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
