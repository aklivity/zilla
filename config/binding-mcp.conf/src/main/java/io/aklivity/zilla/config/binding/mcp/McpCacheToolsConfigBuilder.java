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
package io.aklivity.zilla.config.binding.mcp;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpCacheToolsConfigBuilder<T> extends ConfigBuilder<T, McpCacheToolsConfigBuilder<T>>
{
    private final Function<McpCacheToolsConfig, T> mapper;

    private McpCacheToolsSearchConfig search;
    private McpCacheToolsEagerConfig eager;

    McpCacheToolsConfigBuilder(
        Function<McpCacheToolsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpCacheToolsConfigBuilder<T>> thisType()
    {
        return (Class<McpCacheToolsConfigBuilder<T>>) getClass();
    }

    public McpCacheToolsConfigBuilder<T> search(
        McpCacheToolsSearchConfig search)
    {
        this.search = search;
        return this;
    }

    public McpCacheToolsSearchConfigBuilder<McpCacheToolsConfigBuilder<T>> search()
    {
        return McpCacheToolsSearchConfig.builder(this::search);
    }

    public McpCacheToolsConfigBuilder<T> eager(
        McpCacheToolsEagerConfig eager)
    {
        this.eager = eager;
        return this;
    }

    public McpCacheToolsEagerConfigBuilder<McpCacheToolsConfigBuilder<T>> eager()
    {
        return McpCacheToolsEagerConfig.builder(this::eager);
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpCacheToolsConfig(search, eager));
    }
}
