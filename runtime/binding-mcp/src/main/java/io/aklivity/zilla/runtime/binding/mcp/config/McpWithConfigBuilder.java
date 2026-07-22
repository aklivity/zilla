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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class McpWithConfigBuilder<T> extends ConfigBuilder<T, McpWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private McpWithCacheConfig cache;

    public McpWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public McpWithConfigBuilder<T> cache(
        McpWithCacheConfig cache)
    {
        this.cache = cache;
        return this;
    }

    public McpWithCacheConfigBuilder<McpWithConfigBuilder<T>> cache()
    {
        return McpWithCacheConfig.builder(this::cache);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpWithConfigBuilder<T>> thisType()
    {
        return (Class<McpWithConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpWithConfig(cache));
    }
}
