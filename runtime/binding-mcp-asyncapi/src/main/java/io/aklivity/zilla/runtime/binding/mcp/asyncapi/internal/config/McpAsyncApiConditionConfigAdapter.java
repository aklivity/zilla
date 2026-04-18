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
package io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.McpAsyncApiBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class McpAsyncApiConditionConfigAdapter implements ConditionConfigAdapterSpi,
    JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String OPERATION_NAME = "operation";

    @Override
    public String type()
    {
        return McpAsyncApiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        McpAsyncApiConditionConfig mcpCondition = (McpAsyncApiConditionConfig) condition;
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpCondition.operation != null)
        {
            object.add(OPERATION_NAME, mcpCondition.operation);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String operation = object.containsKey(OPERATION_NAME)
            ? object.getString(OPERATION_NAME)
            : null;

        return new McpAsyncApiConditionConfig(operation);
    }
}
