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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsRequest.Generator;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsRequest.Source.CreationConsumer;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCreateAclsRequestTest
{
    // body bytes only, as verified against the CreateAcls v2 wire encoder output (RequestHeader
    // apiKey/apiVersion/correlationId/clientId are encoded separately and excluded here)
    private static final byte[] EXPECTED = new byte[]
    {
        0x00,
        0x02,
        0x02,
        0x07, 'e', 'v', 'e', 'n', 't', 's',
        0x04,
        0x0b, 'U', 's', 'e', 'r', ':', 'a', 'l', 'i', 'c', 'e',
        0x02, '*',
        0x03,
        0x03,
        0x00,
        0x00
    };

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource(List.of(
            new FakeCreation(KafkaAclTypes.RESOURCE_TYPE_TOPIC, "events", KafkaAclTypes.PATTERN_TYPE_PREFIXED,
                "User:alice", "*", KafkaAclTypes.OPERATION_READ, KafkaAclTypes.PERMISSION_TYPE_ALLOW)));

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
            new FakeCreation(KafkaAclTypes.RESOURCE_TYPE_TOPIC, "events", KafkaAclTypes.PATTERN_TYPE_PREFIXED,
                "User:alice", "*", KafkaAclTypes.OPERATION_READ, KafkaAclTypes.PERMISSION_TYPE_ALLOW)));

        assertEquals(EXPECTED.length, KafkaCreateAclsRequest.sizeof(source, (short) 2));
    }

    @Test
    public void shouldComputeSizeofForMultipleCreations()
    {
        FakeSource source = new FakeSource(List.of(
            new FakeCreation(KafkaAclTypes.RESOURCE_TYPE_TOPIC, "events", KafkaAclTypes.PATTERN_TYPE_LITERAL,
                "User:alice", "*", KafkaAclTypes.OPERATION_READ, KafkaAclTypes.PERMISSION_TYPE_ALLOW),
            new FakeCreation(KafkaAclTypes.RESOURCE_TYPE_GROUP, "my-group", KafkaAclTypes.PATTERN_TYPE_LITERAL,
                "User:bob", "*", KafkaAclTypes.OPERATION_DESCRIBE, KafkaAclTypes.PERMISSION_TYPE_DENY)));

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(source));
        assertEquals(generator.limit(), KafkaCreateAclsRequest.sizeof(source, (short) 2));
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        FakeSource source = new FakeSource(List.of(
            new FakeCreation(KafkaAclTypes.RESOURCE_TYPE_TOPIC, "events-resource-name-too-long-to-fit-in-a-tiny-buffer",
                KafkaAclTypes.PATTERN_TYPE_LITERAL, "User:alice", "*", KafkaAclTypes.OPERATION_READ,
                KafkaAclTypes.PERMISSION_TYPE_ALLOW)));

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertFalse(generator.generate(source));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        FakeSource source = new FakeSource(List.of());

        assertThrows(UnsupportedOperationException.class, () -> KafkaCreateAclsRequest.sizeof(source, (short) 1));
    }

    private record FakeCreation(
        byte resourceType,
        String resourceName,
        byte resourcePatternType,
        String principal,
        String host,
        byte operation,
        byte permissionType) implements Source.Creation
    {
    }

    private record FakeSource(
        List<FakeCreation> creations) implements Source
    {
        @Override
        public int creationCount()
        {
            return creations.size();
        }

        @Override
        public void forEach(
            CreationConsumer consumer)
        {
            creations.forEach(consumer::accept);
        }
    }
}
