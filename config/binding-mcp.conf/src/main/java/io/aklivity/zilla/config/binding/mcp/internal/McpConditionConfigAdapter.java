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
package io.aklivity.zilla.config.binding.mcp.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.mcp.McpConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.runtime.common.json.JsonStrings;

public final class McpConditionConfigAdapter implements JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOOLKIT_NAME = "toolkit";
    private static final String TOOL_NAME = "tool";
    private static final String PROMPT_NAME = "prompt";
    private static final String RESOURCE_NAME = "resource";

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

        JsonStrings.addStringOrArray(object, TOOL_NAME, mcpCondition.tool);
        JsonStrings.addStringOrArray(object, PROMPT_NAME, mcpCondition.prompt);
        JsonStrings.addStringOrArray(object, RESOURCE_NAME, mcpCondition.resource);

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
            .tool(JsonStrings.asStringOrArray(object, TOOL_NAME))
            .prompt(JsonStrings.asStringOrArray(object, PROMPT_NAME))
            .resource(JsonStrings.asStringOrArray(object, RESOURCE_NAME))
            .build();
    }
}
