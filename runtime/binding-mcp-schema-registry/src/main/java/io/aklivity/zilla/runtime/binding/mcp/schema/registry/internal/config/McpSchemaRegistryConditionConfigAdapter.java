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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.McpSchemaRegistryBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class McpSchemaRegistryConditionConfigAdapter implements ConditionConfigAdapterSpi,
    JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOOL_NAME = "tool";

    @Override
    public String type()
    {
        return McpSchemaRegistryBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        McpSchemaRegistryConditionConfig config = (McpSchemaRegistryConditionConfig) condition;
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (config.tool != null)
        {
            object.add(TOOL_NAME, config.tool);
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

        return new McpSchemaRegistryConditionConfig(tool);
    }
}
