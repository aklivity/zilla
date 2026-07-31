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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.DescribeClusterResponse.Broker;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class DescribeClusterResponseV0FWTest
{
    // body bytes only, as verified against the real Kafka DescribeCluster v0 wire decoder input
    // (the response header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x00,
        0x0a, 'c', 'l', 'u', 's', 't', 'e', 'r', '-', '1',
        0x00, 0x00, 0x00, 0x01,
        0x03,
        0x00, 0x00, 0x00, 0x01,
        0x08, 'b', 'r', 'o', 'k', 'e', 'r', '1',
        0x00, 0x00, 0x23, (byte) 0x84,
        0x00,
        0x00,
        0x00, 0x00, 0x00, 0x02,
        0x08, 'b', 'r', 'o', 'k', 'e', 'r', '2',
        0x00, 0x00, 0x23, (byte) 0x85,
        0x07, 'r', 'a', 'c', 'k', '-', 'a',
        0x00,
        0x00, 0x00, 0x00, 0x03,
        0x00
    };

    private static String asString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return length == -1 ? null : buffer.getStringWithoutLengthUtf8(offset, length);
    }

    @Test
    public void shouldDecodeDescribeClusterV0Response()
    {
        DescribeClusterResponseV0FW response = new DescribeClusterResponseV0FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(0, response.error());
        assertEquals(-1, response.messageLength());
        assertEquals("cluster-1", asString(buffer, response.clusterIdOffset(), response.clusterIdLength()));
        assertEquals(1, response.controllerId());
        assertEquals(2, response.brokerCount());

        assertTrue(response.hasNext());
        Broker broker1 = response.next();
        assertEquals(1, broker1.brokerId());
        assertEquals("broker1", asString(broker1.buffer(), broker1.hostOffset(), broker1.hostLength()));
        assertEquals(9092, broker1.port());
        assertEquals(-1, broker1.rackLength());

        assertTrue(response.hasNext());
        Broker broker2 = response.next();
        assertEquals(2, broker2.brokerId());
        assertEquals("broker2", asString(broker2.buffer(), broker2.hostOffset(), broker2.hostLength()));
        assertEquals(9093, broker2.port());
        assertEquals("rack-a", asString(broker2.buffer(), broker2.rackOffset(), broker2.rackLength()));

        assertFalse(response.hasNext());
        assertEquals(3, response.authorizedOperations());
        assertEquals(BODY.length, response.limit());
    }

    @Test
    public void shouldDecodeErrorResponse()
    {
        final byte[] body = new byte[]
        {
            0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x24,
            0x0f, 'n', 'o', 't', ' ', 'a', 'u', 't', 'h', 'o', 'r', 'i', 'z', 'e', 'd',
            0x00,
            0x00, 0x00, 0x00, 0x00,
            0x01,
            0x00, 0x00, 0x00, 0x00, 0x00
        };

        DescribeClusterResponseV0FW response = new DescribeClusterResponseV0FW();

        DirectBufferEx buffer = new UnsafeBufferEx(body);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(36, response.error());
        assertEquals("not authorized", asString(buffer, response.messageOffset(), response.messageLength()));
        assertEquals(-1, response.clusterIdLength());
        assertFalse(response.hasNext());
        assertEquals(0, response.authorizedOperations());
        assertEquals(body.length, response.limit());
    }
}
