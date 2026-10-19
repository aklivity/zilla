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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAclTypes;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsRequest.Source.Creation;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class McpKafkaToolCreateAclsSourceTest
{
    @Test
    public void shouldParseSingleCreation()
    {
        McpKafkaToolCreateAclsSource source = new McpKafkaToolCreateAclsSource();

        Status status = parse(source,
            "{\"arguments\":{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"events\"," +
            "\"principal\":\"User:alice\",\"operation\":\"read\",\"permission_type\":\"allow\"}]}}");

        assertEquals(Status.COMPLETED, status);
        assertTrue(source.completed());
        assertEquals(1, source.creationCount());

        List<Creation> creations = new ArrayList<>();
        source.forEach(creations::add);
        Creation creation = creations.get(0);
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TOPIC, creation.resourceType());
        assertEquals("events", creation.resourceName());
        assertEquals(KafkaAclTypes.PATTERN_TYPE_LITERAL, creation.resourcePatternType());
        assertEquals("User:alice", creation.principal());
        assertEquals("*", creation.host());
        assertEquals(KafkaAclTypes.OPERATION_READ, creation.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ALLOW, creation.permissionType());
    }

    @Test
    public void shouldParseExplicitPatternTypeAndHost()
    {
        McpKafkaToolCreateAclsSource source = new McpKafkaToolCreateAclsSource();

        parse(source,
            "{\"arguments\":{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"eve\"," +
            "\"pattern_type\":\"prefixed\",\"principal\":\"User:alice\",\"host\":\"10.0.0.1\"," +
            "\"operation\":\"write\",\"permission_type\":\"deny\"}]}}");

        List<Creation> creations = new ArrayList<>();
        source.forEach(creations::add);
        Creation creation = creations.get(0);
        assertEquals(KafkaAclTypes.PATTERN_TYPE_PREFIXED, creation.resourcePatternType());
        assertEquals("10.0.0.1", creation.host());
        assertEquals(KafkaAclTypes.OPERATION_WRITE, creation.operation());
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_DENY, creation.permissionType());
    }

    @Test
    public void shouldParseMultipleCreations()
    {
        McpKafkaToolCreateAclsSource source = new McpKafkaToolCreateAclsSource();

        Status status = parse(source,
            "{\"arguments\":{\"acls\":[" +
            "{\"resource_type\":\"topic\",\"resource_name\":\"events\",\"principal\":\"User:alice\"," +
            "\"operation\":\"read\",\"permission_type\":\"allow\"}," +
            "{\"resource_type\":\"group\",\"resource_name\":\"my-group\",\"principal\":\"User:bob\"," +
            "\"operation\":\"describe\",\"permission_type\":\"deny\"}]}}");

        assertEquals(Status.COMPLETED, status);
        assertEquals(2, source.creationCount());
    }

    @Test
    public void shouldRejectMissingRequiredField()
    {
        McpKafkaToolCreateAclsSource source = new McpKafkaToolCreateAclsSource();

        Status status = parse(source,
            "{\"arguments\":{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"events\"," +
            "\"operation\":\"read\",\"permission_type\":\"allow\"}]}}");

        assertEquals(Status.REJECTED, status);
        assertFalse(source.completed());
    }

    @Test
    public void shouldRejectEmptyAclsArray()
    {
        McpKafkaToolCreateAclsSource source = new McpKafkaToolCreateAclsSource();

        Status status = parse(source, "{\"arguments\":{\"acls\":[]}}");

        assertEquals(Status.REJECTED, status);
    }

    @Test
    public void shouldIgnoreMetaSiblingOfArguments()
    {
        McpKafkaToolCreateAclsSource source = new McpKafkaToolCreateAclsSource();

        Status status = parse(source,
            "{\"name\":\"create_acls\",\"arguments\":{\"acls\":[{\"resource_type\":\"topic\"," +
            "\"resource_name\":\"events\",\"principal\":\"User:alice\",\"operation\":\"read\"," +
            "\"permission_type\":\"allow\"}]},\"_meta\":{\"progressToken\":1}}");

        assertEquals(Status.COMPLETED, status);
        assertEquals(1, source.creationCount());
    }

    @Test
    public void shouldResetBetweenCalls()
    {
        McpKafkaToolCreateAclsSource source = new McpKafkaToolCreateAclsSource();

        parse(source,
            "{\"arguments\":{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"events\"," +
            "\"principal\":\"User:alice\",\"operation\":\"read\",\"permission_type\":\"allow\"}]}}");
        assertTrue(source.completed());

        source.reset();

        assertFalse(source.completed());
        assertEquals(0, source.creationCount());
    }

    private static Status parse(
        McpKafkaToolCreateAclsSource source,
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
