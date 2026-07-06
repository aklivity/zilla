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
package io.aklivity.zilla.runtime.binding.mcp.openapi.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class McpOpenapiOptionsConfigBuilder<T> extends ConfigBuilder<T, McpOpenapiOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private List<McpOpenapiSpecificationConfig> specs;
    private List<McpOpenapiToolConfig> tools;

    McpOpenapiOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOpenapiOptionsConfigBuilder<T>> thisType()
    {
        return (Class<McpOpenapiOptionsConfigBuilder<T>>) getClass();
    }

    public McpOpenapiSpecificationConfigBuilder<McpOpenapiOptionsConfigBuilder<T>> spec()
    {
        return new McpOpenapiSpecificationConfigBuilder<>(this::spec);
    }

    public McpOpenapiOptionsConfigBuilder<T> spec(
        McpOpenapiSpecificationConfig spec)
    {
        if (specs == null)
        {
            specs = new ArrayList<>();
        }
        specs.add(spec);
        return this;
    }

    public McpOpenapiToolConfigBuilder<McpOpenapiOptionsConfigBuilder<T>> tool()
    {
        return new McpOpenapiToolConfigBuilder<>(this::tool);
    }

    public McpOpenapiOptionsConfigBuilder<T> tool(
        McpOpenapiToolConfig tool)
    {
        if (tools == null)
        {
            tools = new ArrayList<>();
        }
        tools.add(tool);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOpenapiOptionsConfig(specs, tools));
    }
}
