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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCreateTopicsRequestGenerator.Assignment;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCreateTopicsRequestGenerator.Config;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCreateTopicsRequestGenerator.Request;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCreateTopicsRequestGenerator.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCreateTopicsRequestGeneratorTest
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
        KafkaCreateTopicsRequestGenerator generator = new KafkaCreateTopicsRequestGenerator();

        Request request = new Request(
            List.of(
                new Topic("events", 1, (short) 1,
                    List.of(new Assignment(0, List.of(0))),
                    List.of(new Config("cleanup.policy", "delete"))),
                new Topic("snapshots", 1, (short) 1,
                    List.of(new Assignment(0, List.of(0))),
                    List.of(new Config("cleanup.policy", "compact")))),
            0,
            false);

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        int limit = generator.generate(buffer, 0, buffer.capacity(), request);

        assertEquals(EXPECTED.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED, actual);
    }
}
