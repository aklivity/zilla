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

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeClusterRequest.Generator;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDescribeClusterRequestTest
{
    // body bytes only, as verified against the real Kafka DescribeCluster v0 wire encoder output
    // (RequestHeader apiKey/apiVersion/correlationId/clientId are encoded separately and excluded here)
    private static final byte[] EXPECTED = new byte[]
    {
        0x00,
        0x01,
        0x00
    };

    @Test
    public void shouldGenerateDescribeClusterV0Request()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(true));

        int limit = generator.limit();
        assertEquals(EXPECTED.length, limit);

        byte[] actual = new byte[limit];
        buffer.getBytes(0, actual);
        assertArrayEquals(EXPECTED, actual);
    }

    @Test
    public void shouldGenerateDescribeClusterV0RequestExcludingAuthorizedOperations()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertTrue(generator.generate(false));

        byte[] actual = new byte[generator.limit()];
        buffer.getBytes(0, actual);
        assertArrayEquals(new byte[] { 0x00, 0x00, 0x00 }, actual);
    }

    @Test
    public void shouldRejectWhenBufferTooSmall()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1]);
        Generator generator = new Generator().wrap(buffer, 0, buffer.capacity());

        assertFalse(generator.generate(true));
    }

    @Test
    public void shouldComputeSizeofMatchingGoldenBytes()
    {
        assertEquals(EXPECTED.length, KafkaDescribeClusterRequest.sizeof((short) 0));
    }

    @Test
    public void shouldRejectUnsupportedApiVersion()
    {
        assertThrows(UnsupportedOperationException.class, () -> KafkaDescribeClusterRequest.sizeof((short) 1));
    }
}
