/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenApiConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenApiBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class McpOpenApiConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOOL_NAME = "tool";
    private static final String RESOURCE_NAME = "resource";

    @Override
    public String type()
    {
        return McpOpenApiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        McpOpenApiConditionConfig config = (McpOpenApiConditionConfig) condition;
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (config.tool != null)
        {
            object.add(TOOL_NAME, config.tool);
        }

        if (config.resource != null)
        {
            object.add(RESOURCE_NAME, config.resource);
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

        return new McpOpenApiConditionConfig(tool, resource);
    }
}
