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

import static java.util.stream.Collectors.toList;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class McpKafkaConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOOL_NAME = "tool";
    private static final String RESOURCE_NAME = "resource";
    private static final String TOPICS_NAME = "topics";

    @Override
    public String type()
    {
        return McpKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        McpKafkaConditionConfig mcpKafkaCondition = (McpKafkaConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpKafkaCondition.tool != null)
        {
            object.add(TOOL_NAME, mcpKafkaCondition.tool);
        }

        if (mcpKafkaCondition.resource != null)
        {
            object.add(RESOURCE_NAME, mcpKafkaCondition.resource);
        }

        if (mcpKafkaCondition.topics != null)
        {
            JsonArrayBuilder topics = Json.createArrayBuilder();
            mcpKafkaCondition.topics.forEach(topics::add);
            object.add(TOPICS_NAME, topics);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String tool = object.containsKey(TOOL_NAME)
            ? object.getString(TOOL_NAME)
            : null;

        String resource = object.containsKey(RESOURCE_NAME)
            ? object.getString(RESOURCE_NAME)
            : null;

        List<String> topics = object.containsKey(TOPICS_NAME)
            ? object.getJsonArray(TOPICS_NAME).stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .collect(toList())
            : null;

        return new McpKafkaConditionConfig(tool, resource, topics);
    }
}
