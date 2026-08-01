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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeGroupsResponse.Group;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeGroupsResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeGroupsResponse.Member;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDescribeGroupsResponseV5FWTest
{
    // body bytes only, as verified against the real Kafka DescribeGroups v5 wire decoder input
    // (the response header's correlationId is decoded separately and excluded here)
    private static final byte[] BODY = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x02,
        0x00, 0x00,
        0x09, 'm', 'y', '-', 'g', 'r', 'o', 'u', 'p',
        0x07, 'S', 't', 'a', 'b', 'l', 'e',
        0x09, 'c', 'o', 'n', 's', 'u', 'm', 'e', 'r',
        0x06, 'r', 'a', 'n', 'g', 'e',
        0x02,
        0x0f, 'c', 'o', 'n', 's', 'u', 'm', 'e', 'r', '-', '1', '-', 'a', 'b', 'c',
        0x00,
        0x0b, 'c', 'o', 'n', 's', 'u', 'm', 'e', 'r', '-', '1',
        0x0a, '/', '1', '0', '.', '0', '.', '0', '.', '1',
        0x04, 0x01, 0x02, 0x03,
        0x03, 0x04, 0x05,
        0x00,
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
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

    private static byte[] asBytes(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        return bytes;
    }

    @Test
    public void shouldDecodeDescribeGroupsV5Response()
    {
        KafkaDescribeGroupsResponseV5FW response = new KafkaDescribeGroupsResponseV5FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(1, response.groupCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.GROUP, response.next());

        Group group = response.group();
        assertEquals(0, group.error());
        assertEquals("my-group", asString(group.buffer(), group.groupIdOffset(), group.groupIdLength()));
        assertEquals("Stable", asString(group.buffer(), group.groupStateOffset(), group.groupStateLength()));
        assertEquals("consumer", asString(group.buffer(), group.protocolTypeOffset(), group.protocolTypeLength()));
        assertEquals("range", asString(group.buffer(), group.protocolDataOffset(), group.protocolDataLength()));
        assertEquals(1, group.memberCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.MEMBER, response.next());

        Member member = response.member();
        assertEquals("consumer-1-abc", asString(member.buffer(), member.memberIdOffset(), member.memberIdLength()));
        assertNull(asString(member.buffer(), member.groupInstanceIdOffset(), member.groupInstanceIdLength()));
        assertEquals("consumer-1", asString(member.buffer(), member.clientIdOffset(), member.clientIdLength()));
        assertEquals("/10.0.0.1", asString(member.buffer(), member.clientHostOffset(), member.clientHostLength()));
        assertArrayEquals(new byte[] { 0x01, 0x02, 0x03 },
            asBytes(member.buffer(), member.memberMetadataOffset(), member.memberMetadataLength()));
        assertArrayEquals(new byte[] { 0x04, 0x05 },
            asBytes(member.buffer(), member.memberAssignmentOffset(), member.memberAssignmentLength()));

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
