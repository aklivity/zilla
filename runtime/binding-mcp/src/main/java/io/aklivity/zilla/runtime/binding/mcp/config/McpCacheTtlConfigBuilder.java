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

import java.time.Duration;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class McpCacheTtlConfigBuilder<T> extends ConfigBuilder<T, McpCacheTtlConfigBuilder<T>>
{
    private final Function<McpCacheTtlConfig, T> mapper;

    private Duration tools;
    private Duration resources;
    private Duration prompts;

    McpCacheTtlConfigBuilder(
        Function<McpCacheTtlConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpCacheTtlConfigBuilder<T>> thisType()
    {
        return (Class<McpCacheTtlConfigBuilder<T>>) getClass();
    }

    public McpCacheTtlConfigBuilder<T> tools(
        Duration tools)
    {
        this.tools = tools;
        return this;
    }

    public McpCacheTtlConfigBuilder<T> resources(
        Duration resources)
    {
        this.resources = resources;
        return this;
    }

    public McpCacheTtlConfigBuilder<T> prompts(
        Duration prompts)
    {
        this.prompts = prompts;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpCacheTtlConfig(tools, resources, prompts));
    }
}
