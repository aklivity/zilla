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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsResponse.FilterResult;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsResponse.MatchingAcl;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDeleteAclsResponseV2FWTest
{
    // body bytes only, as verified against the DeleteAcls v2 wire decoder input (the response
    // header's correlationId is decoded separately and excluded here). Two filter results: one with
    // a single matching ACL, one that matched nothing.
    private static final byte[] BODY = new byte[]
    {
        0x00,                                                                // tagged fields (header)
        0x00, 0x00, 0x00, 0x00,                                              // throttle time ms
        0x03,                                                                // filter result count (2)
        0x00, 0x00,                                                          // filter1 error (ok)
        0x00,                                                                // filter1 message (null)
        0x02,                                                                // matching acl count (1)
        0x00, 0x00,                                                          // acl error (ok)
        0x00,                                                                // acl message (null)
        0x02,                                                                // resource type (topic)
        0x07, 'e', 'v', 'e', 'n', 't', 's',                                  // resource name
        0x03,                                                                // pattern type (literal)
        0x0b, 'U', 's', 'e', 'r', ':', 'a', 'l', 'i', 'c', 'e',              // principal
        0x02, '*',                                                          // host
        0x03,                                                                // operation (read)
        0x03,                                                                // permission (allow)
        0x00,                                                                // acl tagged fields
        0x00,                                                                // filter1 tagged fields
        0x00, 0x00,                                                         // filter2 error (ok)
        0x00,                                                                // filter2 message (null)
        0x01,                                                                // matching acl count (0)
        0x00,                                                                // filter2 tagged fields
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
    public void shouldDecodeDeleteAclsV2Response()
    {
        KafkaDeleteAclsResponseV2FW response = new KafkaDeleteAclsResponseV2FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(2, response.filterResultCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.FILTER_RESULT, response.next());
        FilterResult filter1 = response.filterResult();
        assertEquals(0, filter1.error());
        assertEquals(1, filter1.matchingAclCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.MATCHING_ACL, response.next());
        MatchingAcl acl = response.matchingAcl();
        assertEquals(0, acl.error());
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TOPIC, acl.resourceType());
        assertEquals("events", asString(acl.buffer(), acl.resourceNameOffset(), acl.resourceNameLength()));
        assertEquals(KafkaAclTypes.PATTERN_TYPE_LITERAL, acl.patternType());
        assertEquals("User:alice", asString(acl.buffer(), acl.principalOffset(), acl.principalLength()));
        assertEquals("*", asString(acl.buffer(), acl.hostOffset(), acl.hostLength()));
        assertEquals(KafkaAclTypes.OPERATION_READ, acl.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ALLOW, acl.permissionType());

        assertTrue(response.hasNext());
        assertEquals(Kind.FILTER_RESULT, response.next());
        FilterResult filter2 = response.filterResult();
        assertEquals(0, filter2.error());
        assertEquals(0, filter2.matchingAclCount());

        assertFalse(response.hasNext());
        assertEquals(BODY.length, response.limit());
    }
}
