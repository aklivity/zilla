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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfigAdapterSpi;
import io.aklivity.zilla.runtime.binding.mcp.config.McpConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpBinding;

public final class McpConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOOLKIT_NAME = "toolkit";
    private static final String TOOLS_NAME = "tools";
    private static final String PROMPTS_NAME = "prompts";
    private static final String RESOURCES_NAME = "resources";

    @Override
    public String type()
    {
        return McpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        McpConditionConfig mcpCondition = (McpConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpCondition.toolkit != null)
        {
            object.add(TOOLKIT_NAME, mcpCondition.toolkit);
        }

        if (mcpCondition.tools != null)
        {
            JsonArrayBuilder array = Json.createArrayBuilder();
            mcpCondition.tools.forEach(array::add);
            object.add(TOOLS_NAME, array);
        }

        if (mcpCondition.prompts != null)
        {
            JsonArrayBuilder array = Json.createArrayBuilder();
            mcpCondition.prompts.forEach(array::add);
            object.add(PROMPTS_NAME, array);
        }

        if (mcpCondition.resources != null)
        {
            JsonArrayBuilder array = Json.createArrayBuilder();
            mcpCondition.resources.forEach(array::add);
            object.add(RESOURCES_NAME, array);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String toolkit = object.containsKey(TOOLKIT_NAME)
            ? object.getString(TOOLKIT_NAME)
            : null;

        return McpConditionConfig.builder()
            .toolkit(toolkit)
            .tools(asStringList(object, TOOLS_NAME))
            .prompts(asStringList(object, PROMPTS_NAME))
            .resources(asStringList(object, RESOURCES_NAME))
            .build();
    }

    private static List<String> asStringList(
        JsonObject object,
        String name)
    {
        List<String> result = null;
        if (object.containsKey(name))
        {
            JsonArray array = object.getJsonArray(name);
            result = array.stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .collect(toList());
        }
        return result;
    }
}
