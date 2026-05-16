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
package io.aklivity.zilla.runtime.binding.mcp.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class McpCacheConfigBuilder<T> extends ConfigBuilder<T, McpCacheConfigBuilder<T>>
{
    private final Function<McpCacheConfig, T> mapper;

    private String store;
    private McpCacheTtlConfig ttl;
    private Map<String, String> authorization;

    McpCacheConfigBuilder(
        Function<McpCacheConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpCacheConfigBuilder<T>> thisType()
    {
        return (Class<McpCacheConfigBuilder<T>>) getClass();
    }

    public McpCacheConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public McpCacheConfigBuilder<T> ttl(
        McpCacheTtlConfig ttl)
    {
        this.ttl = ttl;
        return this;
    }

    public McpCacheTtlConfigBuilder<McpCacheConfigBuilder<T>> ttl()
    {
        return McpCacheTtlConfig.builder(this::ttl);
    }

    public McpCacheConfigBuilder<T> authorization(
        String guard,
        String credentials)
    {
        if (authorization == null)
        {
            authorization = new LinkedHashMap<>();
        }
        authorization.put(guard, credentials);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpCacheConfig(store, ttl, authorization));
    }
}
