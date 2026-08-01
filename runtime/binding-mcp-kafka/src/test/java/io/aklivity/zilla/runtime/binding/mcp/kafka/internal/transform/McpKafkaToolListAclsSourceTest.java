/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAclTypes;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class McpKafkaToolListAclsSourceTest
{
    @Test
    public void shouldParseFullFilter()
    {
        McpKafkaToolListAclsSource source = new McpKafkaToolListAclsSource();

        Status status = parse(source,
            "{\"arguments\":{\"resource_type\":\"topic\",\"resource_name\":\"events\"," +
            "\"pattern_type\":\"literal\",\"principal\":\"User:alice\",\"host\":\"*\"," +
            "\"operation\":\"read\",\"permission_type\":\"allow\"}}");

        assertEquals(Status.COMPLETED, status);
        assertTrue(source.completed());
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TOPIC, source.resourceType());
        assertEquals("events", source.resourceName());
        assertEquals(KafkaAclTypes.PATTERN_TYPE_LITERAL, source.patternType());
        assertEquals("User:alice", source.principal());
        assertEquals("*", source.host());
        assertEquals(KafkaAclTypes.OPERATION_READ, source.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ALLOW, source.permissionType());
    }

    @Test
    public void shouldParseEmptyFilterAsMatchAny()
    {
        McpKafkaToolListAclsSource source = new McpKafkaToolListAclsSource();

        Status status = parse(source, "{\"arguments\":{}}");

        assertEquals(Status.COMPLETED, status);
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_ANY, source.resourceType());
        assertNull(source.resourceName());
        assertEquals(KafkaAclTypes.PATTERN_TYPE_ANY, source.patternType());
        assertNull(source.principal());
        assertNull(source.host());
        assertEquals(KafkaAclTypes.OPERATION_ANY, source.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ANY, source.permissionType());
    }

    @Test
    public void shouldResetBetweenCalls()
    {
        McpKafkaToolListAclsSource source = new McpKafkaToolListAclsSource();

        parse(source, "{\"arguments\":{\"resource_type\":\"topic\",\"resource_name\":\"events\"}}");
        assertTrue(source.completed());

        source.reset();

        assertEquals(false, source.completed());
        assertNull(source.resourceName());
    }

    private static Status parse(
        McpKafkaToolListAclsSource source,
        String json)
    {
        final JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(source);
        pipeline.reset();

        final byte[] in = json.getBytes(UTF_8);
        final MutableDirectBufferEx src = new UnsafeBufferEx(new byte[in.length]);
        src.putBytes(0, in);

        return pipeline.transform(src, 0, in.length);
    }
}
