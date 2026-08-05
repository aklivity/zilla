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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsResponse.Config;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsResponse.Resource;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaDescribeConfigsResponseV4FWTest
{
    // body bytes only, as verified against the DescribeConfigs v4 wire decoder input (the response
    // header's correlationId is decoded separately and excluded here). One resource with two configs;
    // the first config carries one synonym, the second carries none, exercising both branches of the
    // inline synonym-skip loop.
    private static final byte[] BODY_WITH_CONFIGS = new byte[]
    {
        0x00,                                                               // tagged fields (header)
        0x00, 0x00, 0x00, 0x00,                                             // throttle time ms
        0x02,                                                               // resource count (1)
        0x00, 0x00,                                                         // error
        0x00,                                                               // message (null)
        0x02,                                                               // type (topic)
        0x07, 'e', 'v', 'e', 'n', 't', 's',                                 // name
        0x03,                                                               // config count (2)
        0x0f, 'c', 'l', 'e', 'a', 'n', 'u', 'p', '.', 'p', 'o', 'l', 'i', 'c', 'y', // config1 name
        0x07, 'd', 'e', 'l', 'e', 't', 'e',                                 // config1 value
        0x00,                                                               // read only (false)
        0x05,                                                               // config source (default)
        0x01,                                                               // is sensitive (true)
        0x02,                                                               // synonym count (1)
        0x0f, 'c', 'l', 'e', 'a', 'n', 'u', 'p', '.', 'p', 'o', 'l', 'i', 'c', 'y', // synonym name
        0x08, 'c', 'o', 'm', 'p', 'a', 'c', 't',                            // synonym value
        0x01,                                                               // synonym source
        0x00,                                                               // synonym tagged fields
        0x01,                                                               // config1 type (string)
        0x00,                                                               // config1 documentation (null)
        0x00,                                                               // config1 tagged fields
        0x0d, 'r', 'e', 't', 'e', 'n', 't', 'i', 'o', 'n', '.', 'm', 's',    // config2 name
        0x0a, '6', '0', '4', '8', '0', '0', '0', '0', '0',                  // config2 value
        0x01,                                                               // read only (true)
        0x01,                                                               // config source (not default)
        0x00,                                                               // is sensitive (false)
        0x01,                                                               // synonym count (0)
        0x03,                                                               // config2 type (int)
        0x00,                                                               // config2 documentation (null)
        0x00,                                                               // config2 tagged fields
        0x00,                                                               // resource tagged fields
        0x00                                                                // tagged fields (top)
    };

    // one resource, error and message set, zero configs
    private static final byte[] BODY_WITH_ERROR = new byte[]
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x02,
        0x00, 0x56,                                                         // error (86)
        0x0e, 'u', 'n', 'k', 'n', 'o', 'w', 'n', ' ', 't', 'o', 'p', 'i', 'c', // message
        0x02,                                                               // type (topic)
        0x0a, 's', 'n', 'a', 'p', 's', 'h', 'o', 't', 's',                  // name
        0x01,                                                               // config count (0)
        0x00,                                                               // resource tagged fields
        0x00                                                                // tagged fields (top)
    };

    private static String asString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return length == -1 ? null : buffer.getStringWithoutLengthUtf8(offset, length);
    }

    @Test
    public void shouldDecodeDescribeConfigsV4ResponseWithConfigsAndSynonyms()
    {
        KafkaDescribeConfigsResponseV4FW response = new KafkaDescribeConfigsResponseV4FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY_WITH_CONFIGS);
        response.wrap(buffer, 0, buffer.capacity());

        assertEquals(0, response.throttleTimeMillis());
        assertEquals(1, response.resourceCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.RESOURCE, response.next());
        Resource resource = response.resource();
        assertEquals(0, resource.error());
        assertEquals(-1, resource.messageLength());
        assertEquals(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC, resource.type());
        assertEquals("events", asString(resource.buffer(), resource.nameOffset(), resource.nameLength()));
        assertEquals(2, resource.configCount());

        assertTrue(response.hasNext());
        assertEquals(Kind.CONFIG, response.next());
        Config config1 = response.config();
        assertEquals("cleanup.policy", asString(config1.buffer(), config1.nameOffset(), config1.nameLength()));
        assertEquals("delete", asString(config1.buffer(), config1.valueOffset(), config1.valueLength()));
        assertFalse(config1.readOnly());
        assertEquals(5, config1.configSource());
        assertTrue(config1.isSensitive());

        assertTrue(response.hasNext());
        assertEquals(Kind.CONFIG, response.next());
        Config config2 = response.config();
        assertEquals("retention.ms", asString(config2.buffer(), config2.nameOffset(), config2.nameLength()));
        assertEquals("604800000", asString(config2.buffer(), config2.valueOffset(), config2.valueLength()));
        assertTrue(config2.readOnly());
        assertEquals(1, config2.configSource());
        assertFalse(config2.isSensitive());

        assertFalse(response.hasNext());
        assertEquals(BODY_WITH_CONFIGS.length, response.limit());
    }

    @Test
    public void shouldDecodeDescribeConfigsV4ResponseWithResourceError()
    {
        KafkaDescribeConfigsResponseV4FW response = new KafkaDescribeConfigsResponseV4FW();

        DirectBufferEx buffer = new UnsafeBufferEx(BODY_WITH_ERROR);
        response.wrap(buffer, 0, buffer.capacity());

        assertTrue(response.hasNext());
        assertEquals(Kind.RESOURCE, response.next());
        Resource resource = response.resource();
        assertEquals(86, resource.error());
        assertEquals("unknown topic", asString(resource.buffer(), resource.messageOffset(), resource.messageLength()));
        assertEquals("snapshots", asString(resource.buffer(), resource.nameOffset(), resource.nameLength()));
        assertEquals(0, resource.configCount());

        assertFalse(response.hasNext());
        assertEquals(BODY_WITH_ERROR.length, response.limit());
    }
}
