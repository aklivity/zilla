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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.kafka.McpKafkaConditionConfig;

public class McpKafkaConditionMatcherTest
{
    @Test
    public void shouldMatchAnyTopicWhenTopicsNotGiven()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesTopic("orders"));
        assertTrue(matcher.matchesTopic("shipments"));
    }

    @Test
    public void shouldMatchTopicWithinAllowList()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .topics(asList("orders", "shipments"))
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesTopic("orders"));
        assertTrue(matcher.matchesTopic("shipments"));
    }

    @Test
    public void shouldNotMatchTopicOutsideAllowList()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .topics(asList("orders", "shipments"))
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertFalse(matcher.matchesTopic("payments"));
    }

    @Test
    public void shouldMatchTopicByGlobPrefix()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .topics(asList("orders*"))
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesTopic("orders"));
        assertTrue(matcher.matchesTopic("orders-eu"));
        assertTrue(matcher.matchesTopic("orders-us"));
        assertFalse(matcher.matchesTopic("shipments"));
    }

    @Test
    public void shouldMatchAnyTopicByBareWildcard()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .topics(asList("*"))
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesTopic("orders"));
        assertTrue(matcher.matchesTopic("shipments"));
    }

    @Test
    public void shouldEscapeRegexMetacharactersInLiteralSegments()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .topics(asList("orders.eu"))
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesTopic("orders.eu"));
        assertFalse(matcher.matchesTopic("ordersXeu"));
    }

    @Test
    public void shouldNotMatchAnyTopicWhenAllowListEmpty()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .topics(asList())
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertFalse(matcher.matchesTopic("orders"));
    }

    @Test
    public void shouldMatchToolExactly()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesTool("produce"));
        assertFalse(matcher.matchesTool("consume"));
    }

    @Test
    public void shouldMatchAnyToolWhenToolNotGiven()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .build();
        McpKafkaConditionMatcher matcher = new McpKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesTool("produce"));
        assertTrue(matcher.matchesTool("consume"));
    }
}
