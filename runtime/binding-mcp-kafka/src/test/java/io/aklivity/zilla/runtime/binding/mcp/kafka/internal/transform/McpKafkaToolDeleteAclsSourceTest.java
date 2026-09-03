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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAclTypes;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsRequest.Source.Filter;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class McpKafkaToolDeleteAclsSourceTest
{
    @Test
    public void shouldParseFullFilter()
    {
        McpKafkaToolDeleteAclsSource source = new McpKafkaToolDeleteAclsSource();

        Status status = parse(source,
            "{\"arguments\":{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"events\"," +
            "\"principal\":\"User:alice\",\"operation\":\"read\",\"permission_type\":\"allow\"}]}}");

        assertEquals(Status.COMPLETED, status);
        assertTrue(source.completed());
        assertEquals(1, source.filterCount());

        List<Filter> filters = new ArrayList<>();
        source.forEach(filters::add);
        Filter filter = filters.get(0);
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TOPIC, filter.resourceType());
        assertEquals("events", filter.resourceName());
        assertEquals("User:alice", filter.principal());
        assertEquals(KafkaAclTypes.OPERATION_READ, filter.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ALLOW, filter.permissionType());
    }

    @Test
    public void shouldParseWildcardFilterWithOnlyResourceType()
    {
        McpKafkaToolDeleteAclsSource source = new McpKafkaToolDeleteAclsSource();

        Status status = parse(source, "{\"arguments\":{\"acls\":[{\"resource_type\":\"topic\"}]}}");

        assertEquals(Status.COMPLETED, status);

        List<Filter> filters = new ArrayList<>();
        source.forEach(filters::add);
        Filter filter = filters.get(0);
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TOPIC, filter.resourceType());
        assertNull(filter.resourceName());
        assertEquals(KafkaAclTypes.PATTERN_TYPE_ANY, filter.patternType());
        assertNull(filter.principal());
        assertNull(filter.host());
        assertEquals(KafkaAclTypes.OPERATION_ANY, filter.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ANY, filter.permissionType());
    }

    @Test
    public void shouldAcceptAllWildcardFilter()
    {
        McpKafkaToolDeleteAclsSource source = new McpKafkaToolDeleteAclsSource();

        Status status = parse(source, "{\"arguments\":{\"acls\":[{}]}}");

        assertEquals(Status.COMPLETED, status);
        assertEquals(1, source.filterCount());
    }

    @Test
    public void shouldRejectEmptyAclsArray()
    {
        McpKafkaToolDeleteAclsSource source = new McpKafkaToolDeleteAclsSource();

        Status status = parse(source, "{\"arguments\":{\"acls\":[]}}");

        assertEquals(Status.REJECTED, status);
        assertFalse(source.completed());
    }

    @Test
    public void shouldIgnoreMetaSiblingOfArguments()
    {
        McpKafkaToolDeleteAclsSource source = new McpKafkaToolDeleteAclsSource();

        Status status = parse(source,
            "{\"name\":\"delete_acls\",\"arguments\":{\"acls\":[{\"resource_type\":\"topic\"}]}," +
            "\"_meta\":{\"progressToken\":1}}");

        assertEquals(Status.COMPLETED, status);
        assertEquals(1, source.filterCount());
    }

    @Test
    public void shouldResetBetweenCalls()
    {
        McpKafkaToolDeleteAclsSource source = new McpKafkaToolDeleteAclsSource();

        parse(source, "{\"arguments\":{\"acls\":[{\"resource_type\":\"topic\"}]}}");
        assertTrue(source.completed());

        source.reset();

        assertFalse(source.completed());
        assertEquals(0, source.filterCount());
    }

    private static Status parse(
        McpKafkaToolDeleteAclsSource source,
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
