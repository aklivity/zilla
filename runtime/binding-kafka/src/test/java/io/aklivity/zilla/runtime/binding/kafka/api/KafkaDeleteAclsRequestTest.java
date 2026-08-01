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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsRequest.Generator;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsRequest.Source.FilterConsumer;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDeleteAclsRequestTest
{
    // body bytes only, as verified against the DeleteAcls v2 wire encoder output (RequestHeader
    // apiKey/apiVersion/correlationId/clientId are encoded separately and excluded here)
    private static final byte[] EXPECTED = new byte[]
    {
        0x00,
        0x02,
        0x02,
        0x07, 'e', 'v', 'e', 'n', 't', 's',
        0x03,
        0x0b, 'U', 's', 'e', 'r', ':', 'a', 'l', 'i', 'c', 'e',
        0x00,
        0x01,
        0x03,
        0x00,
        0x00
    };

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource(List.of(
            new FakeFilter(KafkaAclTypes.RESOURCE_TYPE_TOPIC, "events", KafkaAclTypes.PATTERN_TYPE_LITERAL,
                "User:alice", null, KafkaAclTypes.OPERATION_ANY, KafkaAclTypes.PERMISSION_TYPE_ALLOW)));

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
        FakeSource source = new FakeSource(List.of(
            new FakeFilter(KafkaAclTypes.RESOURCE_TYPE_TOPIC, "events", KafkaAclTypes.PATTERN_TYPE_LITERAL,
                "User:alice", null, KafkaAclTypes.OPERATION_ANY, KafkaAclTypes.PERMISSION_TYPE_ALLOW)));

        assertEquals(EXPECTED.length, KafkaDeleteAclsRequest.sizeof(source, (short) 2));
    }

    @Test
    public void shouldComputeSizeofForAllWildcardFilter()
    {
        FakeSource source = new FakeSource(List.of(
            new FakeFilter(KafkaAclTypes.RESOURCE_TYPE_ANY, null, KafkaAclTypes.PATTERN_TYPE_ANY,
                null, null, KafkaAclTypes.OPERATION_ANY, KafkaAclTypes.PERMISSION_TYPE_ANY)));

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaDeleteAclsRequest.sizeof(source, (short) 2));
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        FakeSource source = new FakeSource(List.of(
            new FakeFilter(KafkaAclTypes.RESOURCE_TYPE_TOPIC, "events-resource-name-too-long-to-fit-in-a-tiny-buffer",
                KafkaAclTypes.PATTERN_TYPE_LITERAL, null, null, KafkaAclTypes.OPERATION_ANY,
                KafkaAclTypes.PERMISSION_TYPE_ANY)));

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertFalse(generator.generate(source));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        FakeSource source = new FakeSource(List.of());

        assertThrows(UnsupportedOperationException.class, () -> KafkaDeleteAclsRequest.sizeof(source, (short) 1));
    }

    private record FakeFilter(
        byte resourceType,
        String resourceName,
        byte patternType,
        String principal,
        String host,
        byte operation,
        byte permissionType) implements Source.Filter
    {
    }

    private record FakeSource(
        List<FakeFilter> filters) implements Source
    {
        @Override
        public int filterCount()
        {
            return filters.size();
        }

        @Override
        public void forEach(
            FilterConsumer consumer)
        {
            filters.forEach(consumer::accept);
        }
    }
}
