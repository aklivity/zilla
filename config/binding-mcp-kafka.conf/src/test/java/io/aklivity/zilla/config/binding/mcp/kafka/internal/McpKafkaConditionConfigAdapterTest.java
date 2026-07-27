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
package io.aklivity.zilla.config.binding.mcp.kafka.internal;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.kafka.McpKafkaConditionConfig;

public class McpKafkaConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpKafkaConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"tool\": \"produce\"," +
                    "\"resource\": \"orders\"," +
                    "\"topics\": [\"orders\", \"shipments\"]" +
                "}";

        McpKafkaConditionConfig condition = jsonb.fromJson(text, McpKafkaConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, equalTo("produce"));
        assertThat(condition.resource, equalTo("orders"));
        assertThat(condition.topics, contains("orders", "shipments"));
    }

    @Test
    public void shouldWriteCondition()
    {
        McpKafkaConditionConfig condition = McpKafkaConditionConfig.builder()
            .tool("produce")
            .resource("orders")
            .topics(asList("orders", "shipments"))
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"tool\":\"produce\",\"resource\":\"orders\",\"topics\":[\"orders\",\"shipments\"]}"));
    }
}
