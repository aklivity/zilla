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

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class McpKafkaToolDescribeConfigsSourceTest
{
    @Test
    public void shouldParseTopicResource()
    {
        McpKafkaToolDescribeConfigsSource source =
            new McpKafkaToolDescribeConfigsSource(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC);

        Status status = parse(source, "{\"arguments\":{\"resource_name\":\"events\"}}");

        assertEquals(Status.COMPLETED, status);
        assertTrue(source.completed());
        assertEquals(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC, source.type());
        assertEquals("events", source.name());
        assertEquals(KafkaDescribeConfigsRequest.ALL_CONFIGS, source.configCount());
    }

    @Test
    public void shouldParseBrokerResource()
    {
        McpKafkaToolDescribeConfigsSource source =
            new McpKafkaToolDescribeConfigsSource(KafkaDescribeConfigsRequest.RESOURCE_TYPE_BROKER);

        Status status = parse(source, "{\"arguments\":{\"resource_name\":\"0\"}}");

        assertEquals(Status.COMPLETED, status);
        assertEquals(KafkaDescribeConfigsRequest.RESOURCE_TYPE_BROKER, source.type());
        assertEquals("0", source.name());
    }

    @Test
    public void shouldRejectMissingResourceName()
    {
        McpKafkaToolDescribeConfigsSource source =
            new McpKafkaToolDescribeConfigsSource(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC);

        Status status = parse(source, "{\"arguments\":{}}");

        assertEquals(Status.REJECTED, status);
        assertFalse(source.completed());
    }

    @Test
    public void shouldResetBetweenCalls()
    {
        McpKafkaToolDescribeConfigsSource source =
            new McpKafkaToolDescribeConfigsSource(KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC);

        parse(source, "{\"arguments\":{\"resource_name\":\"events\"}}");
        assertTrue(source.completed());

        source.reset();

        assertFalse(source.completed());
    }

    private static Status parse(
        McpKafkaToolDescribeConfigsSource source,
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
