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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeAclsResponse.Acl;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeAclsResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeAclsResponse.Resource;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDescribeAclsResponseV2FWTest
{
    // body bytes only, as verified against the DescribeAcls v2 wire decoder input (the response
    // header's correlationId is decoded separately and excluded here). One resource with two ACLs.
    private static final byte[] BODY_WITH_ACLS = new byte[]
    {
        0x00,                                                                // tagged fields (header)
        0x00, 0x00, 0x00, 0x00,                                              // throttle time ms
        0x00, 0x00,                                                          // error
        0x00,                                                                // message (null)
        0x02,                                                                // resource count (1)
        0x02,                                                                // type (topic)
        0x07, 'e', 'v', 'e', 'n', 't', 's',                                  // name
        0x03,                                                                // pattern type (literal)
        0x03,                                                                // acl count (2)
        0x0b, 'U', 's', 'e', 'r', ':', 'a', 'l', 'i', 'c', 'e',              // acl1 principal
        0x02, '*',                                                           // acl1 host
        0x03,                                                                // acl1 operation (read)
        0x03,                                                                // acl1 permission (allow)
        0x00,                                                                // acl1 tagged fields
        0x09, 'U', 's', 'e', 'r', ':', 'b', 'o', 'b',                        // acl2 principal
        0x02, '*',                                                           // acl2 host
        0x04,                                                                // acl2 operation (write)
        0x02,                                                                // acl2 permission (deny)
        0x00,                                                                // acl2 tagged fields
        0x00,                                                                // resource tagged fields
        0x00                                                                 // tagged fields (top)
    };

    // request-level error, zero resources
    private static final byte[] BODY_WITH_ERROR = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x25,                                                          // error (37, unsupported version)
        0x14, 'u', 'n', 's', 'u', 'p', 'p', 'o', 'r', 't', 'e', 'd', ' ', 'v', 'e', 'r', 's', 'i', 'o', 'n',
        0x01,                                                                // resource count (0)
        0x00                                                                 // tagged fields (top)
    };

    private static String asString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return length == -1 ? null : buffer.getStringWithoutLengthUtf8(offset, length);
    }

    @Test
    public void shouldDecodeDescribeAclsV2ResponseWithAcls()
    {
        KafkaDescribeAclsResponseV2FW response = new KafkaDescribeAclsResponseV2FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY_WITH_ACLS);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(0, response.error());
        assertEquals(-1, response.messageLength());
        assertEquals(1, response.resourceCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.RESOURCE, response.next());
        Resource resource = response.resource();
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TOPIC, resource.type());
        assertEquals("events", asString(resource.buffer(), resource.nameOffset(), resource.nameLength()));
        assertEquals(KafkaAclTypes.PATTERN_TYPE_LITERAL, resource.patternType());
        assertEquals(2, resource.aclCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.ACL, response.next());
        Acl acl1 = response.acl();
        assertEquals("User:alice", asString(acl1.buffer(), acl1.principalOffset(), acl1.principalLength()));
        assertEquals("*", asString(acl1.buffer(), acl1.hostOffset(), acl1.hostLength()));
        assertEquals(KafkaAclTypes.OPERATION_READ, acl1.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ALLOW, acl1.permissionType());

        assertTrue(response.hasNext());
        assertEquals(Kind.ACL, response.next());
        Acl acl2 = response.acl();
        assertEquals("User:bob", asString(acl2.buffer(), acl2.principalOffset(), acl2.principalLength()));
        assertEquals(KafkaAclTypes.OPERATION_WRITE, acl2.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_DENY, acl2.permissionType());

        assertFalse(response.hasNext());
        assertEquals(BODY_WITH_ACLS.length, response.limit());
    }

    @Test
    public void shouldDecodeDescribeAclsV2ResponseWithRequestError()
    {
        KafkaDescribeAclsResponseV2FW response = new KafkaDescribeAclsResponseV2FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY_WITH_ERROR);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(37, response.error());
        assertEquals("unsupported version", asString(response.buffer(), response.messageOffset(), response.messageLength()));
        assertEquals(0, response.resourceCount());

        assertFalse(response.hasNext());
        assertEquals(BODY_WITH_ERROR.length, response.limit());
    }
}
