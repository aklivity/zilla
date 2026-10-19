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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class McpKafkaToolAllTopicsSourceTest
{
    @Test
    public void shouldCompleteWithEmptyArguments()
    {
        McpKafkaToolAllTopicsSource source = new McpKafkaToolAllTopicsSource();

        Status status = parse(source, "{\"arguments\":{}}");

        assertEquals(Status.COMPLETED, status);
        assertTrue(source.completed());
        assertTrue(source.allTopics());
        assertEquals(0, source.topicCount());
    }

    @Test
    public void shouldIgnoreMetaSiblingOfArguments()
    {
        McpKafkaToolAllTopicsSource source = new McpKafkaToolAllTopicsSource();

        Status status = parse(source,
            "{\"name\":\"list_topics\",\"arguments\":{},\"_meta\":{\"progressToken\":1}}");

        assertEquals(Status.COMPLETED, status);
        assertTrue(source.completed());
    }

    @Test
    public void shouldResetBetweenCalls()
    {
        McpKafkaToolAllTopicsSource source = new McpKafkaToolAllTopicsSource();

        parse(source, "{\"arguments\":{}}");
        assertTrue(source.completed());

        source.reset();

        assertEquals(false, source.completed());
    }

    private static Status parse(
        McpKafkaToolAllTopicsSource source,
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
