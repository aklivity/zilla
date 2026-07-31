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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeGroupsRequest.Generator;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeGroupsRequest.Source;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDescribeGroupsRequestTest
{
    // body bytes only, as verified against the real Kafka DescribeGroups v5 wire encoder output
    // (RequestHeader apiKey/apiVersion/correlationId/clientId are encoded separately and excluded here)
    private static final byte[] EXPECTED = new byte[]
    {
        0x00, // request header tagged fields
        0x02,
        0x09, 'm', 'y', '-', 'g', 'r', 'o', 'u', 'p',
        0x00,
        0x00
    };

    @Test
    public void shouldGenerateDescribeGroupsV5Request()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.groups(1);
        generator.group("my-group");

        assertTrue(generator.build(false));

        int limit = generator.limit();
        assertEquals(EXPECTED.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED, actual);
    }

    @Test
    public void shouldRejectGroupCountMismatch()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.groups(2);
        generator.group("my-group");

        assertFalse(generator.build(false));
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        generator.groups(1);
        generator.group("group-name-too-long-to-fit");

        assertFalse(generator.build(false));
    }

    @Test
    public void shouldGenerateFromSourceMatchingGoldenBytes()
    {
        FakeSource source = new FakeSource(List.of("my-group"), false);

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
        FakeSource source = new FakeSource(List.of("my-group"), false);

        assertEquals(EXPECTED.length, KafkaDescribeGroupsRequest.sizeof(source, (short) 5));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        FakeSource source = new FakeSource(List.of(), false);

        assertThrows(UnsupportedOperationException.class, () -> KafkaDescribeGroupsRequest.sizeof(source, (short) 3));
    }

    private record FakeSource(
        List<String> groups,
        boolean includeAuthorizedOperations) implements Source
    {
        @Override
        public int groupCount()
        {
            return groups.size();
        }

        @Override
        public void forEach(
            Consumer<String> consumer)
        {
            groups.forEach(consumer);
        }
    }
}
