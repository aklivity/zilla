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
package io.aklivity.zilla.config.binding.mcp.kafka.connect.internal;

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

import io.aklivity.zilla.config.binding.mcp.kafka.connect.McpKafkaConnectConditionConfig;

public class McpKafkaConnectConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpKafkaConnectConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"tool\": \"list_connectors\"" +
                "}";

        McpKafkaConnectConditionConfig condition = jsonb.fromJson(text, McpKafkaConnectConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, contains("list_connectors"));
    }

    @Test
    public void shouldWriteCondition()
    {
        McpKafkaConnectConditionConfig condition = McpKafkaConnectConditionConfig.builder()
            .tool(asList("list_connectors"))
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"tool\":\"list_connectors\"}"));
    }

    @Test
    public void shouldReadToolArray()
    {
        String text = "{\"tool\": [\"list_connectors\", \"describe_connector\"]}";

        McpKafkaConnectConditionConfig condition = jsonb.fromJson(text, McpKafkaConnectConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, contains("list_connectors", "describe_connector"));
    }

    @Test
    public void shouldWriteToolArray()
    {
        McpKafkaConnectConditionConfig condition = McpKafkaConnectConditionConfig.builder()
            .tool(asList("list_connectors", "describe_connector"))
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"tool\":[\"list_connectors\",\"describe_connector\"]}"));
    }
}
