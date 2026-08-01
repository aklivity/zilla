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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListGroupsResponse.Group;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaListGroupsResponseV4FWTest
{
    // body bytes only, as verified against the real Kafka ListGroups v4 wire decoder input
    // (the response header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x03,
        0x09, 'm', 'y', '-', 'g', 'r', 'o', 'u', 'p',
        0x09, 'c', 'o', 'n', 's', 'u', 'm', 'e', 'r',
        0x07, 'S', 't', 'a', 'b', 'l', 'e',
        0x00,
        0x0c, 'o', 't', 'h', 'e', 'r', '-', 'g', 'r', 'o', 'u', 'p',
        0x01,
        0x06, 'E', 'm', 'p', 't', 'y',
        0x00,
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
    public void shouldDecodeListGroupsV4Response()
    {
        KafkaListGroupsResponseV4FW response = new KafkaListGroupsResponseV4FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.error());
        assertEquals(2, response.groupCount());

        assertTrue(response.hasNext());
        Group group1 = response.next();
        assertEquals("my-group", asString(group1.buffer(), group1.groupIdOffset(), group1.groupIdLength()));
        assertEquals("consumer", asString(group1.buffer(), group1.protocolTypeOffset(), group1.protocolTypeLength()));
        assertEquals("Stable", asString(group1.buffer(), group1.groupStateOffset(), group1.groupStateLength()));

        assertTrue(response.hasNext());
        Group group2 = response.next();
        assertEquals("other-group", asString(group2.buffer(), group2.groupIdOffset(), group2.groupIdLength()));
        assertEquals("", asString(group2.buffer(), group2.protocolTypeOffset(), group2.protocolTypeLength()));
        assertEquals("Empty", asString(group2.buffer(), group2.groupStateOffset(), group2.groupStateLength()));

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
