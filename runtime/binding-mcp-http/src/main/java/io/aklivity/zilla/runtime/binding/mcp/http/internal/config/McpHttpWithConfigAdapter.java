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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpBinding;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class McpHttpWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String METHOD_NAME = "method";
    private static final String HEADERS_NAME = "headers";

    @Override
    public String type()
    {
        return McpHttpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        McpHttpWithConfig mcpHttpWith = (McpHttpWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpHttpWith.method != null)
        {
            object.add(METHOD_NAME, mcpHttpWith.method);
        }

        if (mcpHttpWith.headers != null && !mcpHttpWith.headers.isEmpty())
        {
            JsonObjectBuilder headersBuilder = Json.createObjectBuilder();
            mcpHttpWith.headers.forEach(headersBuilder::add);
            object.add(HEADERS_NAME, headersBuilder);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String method = object.containsKey(METHOD_NAME)
            ? object.getString(METHOD_NAME)
            : null;

        Map<String, String> headers = null;
        if (object.containsKey(HEADERS_NAME))
        {
            JsonObject headersJson = object.getJsonObject(HEADERS_NAME);
            headers = new LinkedHashMap<>();
            for (Map.Entry<String, JsonValue> entry : headersJson.entrySet())
            {
                headers.put(entry.getKey(), ((JsonString) entry.getValue()).getString());
            }
        }

        return new McpHttpWithConfig(method, headers);
    }
}
