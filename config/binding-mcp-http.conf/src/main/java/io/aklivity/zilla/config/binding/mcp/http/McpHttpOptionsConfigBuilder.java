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
package io.aklivity.zilla.config.binding.mcp.http;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpHttpOptionsConfigBuilder<T> extends ConfigBuilder<T, McpHttpOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private McpHttpAuthorizationConfig authorization;
    private List<McpHttpToolConfig> tools;
    private List<McpHttpResourceConfig> resources;

    McpHttpOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpHttpOptionsConfigBuilder<T>> thisType()
    {
        return (Class<McpHttpOptionsConfigBuilder<T>>) getClass();
    }

    public McpHttpOptionsConfigBuilder<T> authorization(
        McpHttpAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public McpHttpOptionsConfigBuilder<T> tools(
        List<McpHttpToolConfig> tools)
    {
        this.tools = tools;
        return this;
    }

    public McpHttpOptionsConfigBuilder<T> tool(
        McpHttpToolConfig tool)
    {
        if (tools == null)
        {
            tools = new LinkedList<>();
        }
        tools.add(tool);
        return this;
    }

    public McpHttpOptionsConfigBuilder<T> resources(
        List<McpHttpResourceConfig> resources)
    {
        this.resources = resources;
        return this;
    }

    public McpHttpOptionsConfigBuilder<T> resource(
        McpHttpResourceConfig resource)
    {
        if (resources == null)
        {
            resources = new LinkedList<>();
        }
        resources.add(resource);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpHttpOptionsConfig(authorization, tools, resources));
    }
}
