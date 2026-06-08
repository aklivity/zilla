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

import io.aklivity.zilla.runtime.binding.mcp.config.McpWithCacheConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpBinding;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class McpWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String CACHE_NAME = "cache";
    private static final String CACHE_CREDENTIALS_NAME = "credentials";

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

        if (mcpWith.cache != null && mcpWith.cache.credentials != null)
        {
            JsonObjectBuilder cache = Json.createObjectBuilder();
            cache.add(CACHE_CREDENTIALS_NAME, mcpWith.cache.credentials);
            object.add(CACHE_NAME, cache);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        McpWithConfigBuilder<McpWithConfig> builder = McpWithConfig.builder();

        if (object.containsKey(CACHE_NAME))
        {
            final JsonObject cacheObject = object.getJsonObject(CACHE_NAME);
            final McpWithCacheConfigBuilder<McpWithConfigBuilder<McpWithConfig>> cacheBuilder = builder.cache();
            if (cacheObject.containsKey(CACHE_CREDENTIALS_NAME))
            {
                cacheBuilder.credentials(cacheObject.getString(CACHE_CREDENTIALS_NAME));
            }
            cacheBuilder.build();
        }

        return builder.build();
    }
}
