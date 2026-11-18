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
package io.aklivity.zilla.config.binding.mcp.grpc.internal;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcWithConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcWithConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.WithConfig;

public final class McpGrpcWithConfigAdapter extends ConfigAdapter<WithConfig, JsonObject>
{
    private static final String METHOD_NAME = "method";
    private static final String OPTION_NAME = "option";

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        McpGrpcWithConfig mcpGrpcWith = (McpGrpcWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpGrpcWith.method != null)
        {
            object.add(METHOD_NAME, mcpGrpcWith.method);
        }

        if (mcpGrpcWith.option != null && !mcpGrpcWith.option.isEmpty())
        {
            final JsonObjectBuilder option = Json.createObjectBuilder();
            mcpGrpcWith.option.forEach(option::add);
            object.add(OPTION_NAME, option);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        McpGrpcWithConfigBuilder<McpGrpcWithConfig> with = McpGrpcWithConfig.builder();

        if (object.containsKey(METHOD_NAME))
        {
            with.method(object.getString(METHOD_NAME));
        }

        if (object.containsKey(OPTION_NAME))
        {
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(OPTION_NAME).entrySet())
            {
                with.option(entry.getKey(), entry.getValue());
            }
        }

        return with.build();
    }
}
