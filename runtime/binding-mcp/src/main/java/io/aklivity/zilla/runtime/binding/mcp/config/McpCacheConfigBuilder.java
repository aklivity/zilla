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
package io.aklivity.zilla.runtime.binding.mcp.config;

import java.time.Duration;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class McpCacheConfigBuilder<T> extends ConfigBuilder<T, McpCacheConfigBuilder<T>>
{
    private final Function<McpCacheConfig, T> mapper;

    private String store;
    private Duration ttl;
    private McpAuthorizationConfig authorization;
    private McpCacheToolsConfig tools;

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
        Duration ttl)
    {
        this.ttl = ttl;
        return this;
    }

    public McpCacheConfigBuilder<T> authorization(
        McpAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public McpAuthorizationConfigBuilder<McpCacheConfigBuilder<T>> authorization()
    {
        return McpAuthorizationConfig.builder(this::authorization);
    }

    public McpCacheConfigBuilder<T> tools(
        McpCacheToolsConfig tools)
    {
        this.tools = tools;
        return this;
    }

    public McpCacheToolsConfigBuilder<McpCacheConfigBuilder<T>> tools()
    {
        return McpCacheToolsConfig.builder(this::tools);
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpCacheConfig(store, ttl, authorization, tools));
    }
}
