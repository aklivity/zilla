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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpHttpConditionConfigBuilder<T> extends ConfigBuilder<T, McpHttpConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String tool;
    private String resource;

    McpHttpConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpHttpConditionConfigBuilder<T>> thisType()
    {
        return (Class<McpHttpConditionConfigBuilder<T>>) getClass();
    }

    public McpHttpConditionConfigBuilder<T> tool(
        String tool)
    {
        this.tool = tool;
        return this;
    }

    public McpHttpConditionConfigBuilder<T> resource(
        String resource)
    {
        this.resource = resource;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpHttpConditionConfig(tool, resource));
    }
}
