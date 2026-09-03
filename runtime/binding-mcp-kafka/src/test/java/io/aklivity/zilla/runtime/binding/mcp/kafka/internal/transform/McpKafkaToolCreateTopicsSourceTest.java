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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest.Source.Topic;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class McpKafkaToolCreateTopicsSourceTest
{
    @Test
    public void shouldParseSingleTopic()
    {
        McpKafkaToolCreateTopicsSource source = new McpKafkaToolCreateTopicsSource(30000);

        Status status = parse(source,
            "{\"arguments\":{\"topics\":[{\"topic\":\"events\",\"partitions\":1,\"replicas\":1}]}}");

        assertEquals(Status.COMPLETED, status);
        assertTrue(source.completed());
        assertEquals(1, source.topicCount());

        List<Topic> topics = new ArrayList<>();
        source.forEach(topics::add);
        Topic topic = topics.get(0);
        assertEquals("events", topic.name());
        assertEquals(1, topic.partitions());
        assertEquals(1, topic.replicas());
    }

    @Test
    public void shouldIgnoreMetaSiblingOfArguments()
    {
        McpKafkaToolCreateTopicsSource source = new McpKafkaToolCreateTopicsSource(30000);

        Status status = parse(source,
            "{\"name\":\"create_topics\",\"arguments\":{\"topics\":[{\"topic\":\"events\"," +
            "\"partitions\":1,\"replicas\":1}]},\"_meta\":{\"progressToken\":1}}");

        assertEquals(Status.COMPLETED, status);
        assertEquals(1, source.topicCount());
    }

    @Test
    public void shouldRejectEmptyTopicsArray()
    {
        McpKafkaToolCreateTopicsSource source = new McpKafkaToolCreateTopicsSource(30000);

        Status status = parse(source, "{\"arguments\":{\"topics\":[]}}");

        assertEquals(Status.REJECTED, status);
        assertFalse(source.completed());
    }

    @Test
    public void shouldResetBetweenCalls()
    {
        McpKafkaToolCreateTopicsSource source = new McpKafkaToolCreateTopicsSource(30000);

        parse(source, "{\"arguments\":{\"topics\":[{\"topic\":\"events\",\"partitions\":1,\"replicas\":1}]}}");
        assertTrue(source.completed());

        source.reset();

        assertFalse(source.completed());
        assertEquals(0, source.topicCount());
    }

    private static Status parse(
        McpKafkaToolCreateTopicsSource source,
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
