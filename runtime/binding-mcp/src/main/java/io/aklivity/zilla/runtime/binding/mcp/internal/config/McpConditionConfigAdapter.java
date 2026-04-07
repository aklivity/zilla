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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.config.McpConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class McpConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOOLKIT_NAME = "toolkit";
    private static final String CAPABILITY_NAME = "capability";

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

        if (mcpCondition.capability != null)
        {
            object.add(CAPABILITY_NAME, mcpCondition.capability);
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

        String capability = object.containsKey(CAPABILITY_NAME)
            ? object.getString(CAPABILITY_NAME)
            : null;

        return McpConditionConfig.builder()
            .toolkit(toolkit)
            .capability(capability)
            .build();
    }
}
