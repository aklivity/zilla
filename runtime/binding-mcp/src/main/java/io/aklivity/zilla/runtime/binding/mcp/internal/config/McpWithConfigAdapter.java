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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpBinding;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class McpWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String HEADERS_NAME = "headers";

    @Override
    public String type()
    {
        return McpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        McpWithConfig mcpWith = (McpWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpWith.headers != null && !mcpWith.headers.isEmpty())
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            mcpWith.headers.forEach(headers::add);
            object.add(HEADERS_NAME, headers);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        McpWithConfigBuilder<McpWithConfig> builder = McpWithConfig.builder();

        if (object.containsKey(HEADERS_NAME))
        {
            final JsonObject headersObject = object.getJsonObject(HEADERS_NAME);
            final Map<String, String> headers = new LinkedHashMap<>();
            headersObject.forEach((k, v) -> headers.put(k, headersObject.getString(k)));
            headers.forEach(builder::header);
        }

        return builder.build();
    }
}
