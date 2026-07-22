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
package io.aklivity.zilla.runtime.binding.mcp.openapi.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpOpenapiConditionConfigBuilder<T> extends ConfigBuilder<T, McpOpenapiConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String tool;
    private String resource;
    private List<String> capability;

    McpOpenapiConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOpenapiConditionConfigBuilder<T>> thisType()
    {
        return (Class<McpOpenapiConditionConfigBuilder<T>>) getClass();
    }

    public McpOpenapiConditionConfigBuilder<T> tool(
        String tool)
    {
        this.tool = tool;
        return this;
    }

    public McpOpenapiConditionConfigBuilder<T> resource(
        String resource)
    {
        this.resource = resource;
        return this;
    }

    public McpOpenapiConditionConfigBuilder<T> capability(
        List<String> capability)
    {
        this.capability = capability;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOpenapiConditionConfig(tool, resource, capability));
    }
}
