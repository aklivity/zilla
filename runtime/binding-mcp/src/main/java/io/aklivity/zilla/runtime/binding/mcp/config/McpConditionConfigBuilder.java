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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class McpConditionConfigBuilder<T> extends ConfigBuilder<T, McpConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String toolkit;
    private List<String> tools;
    private List<String> prompts;
    private List<String> resources;

    public McpConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public McpConditionConfigBuilder<T> toolkit(
        String toolkit)
    {
        this.toolkit = toolkit;
        return this;
    }

    public McpConditionConfigBuilder<T> tools(
        List<String> tools)
    {
        this.tools = tools;
        return this;
    }

    public McpConditionConfigBuilder<T> prompts(
        List<String> prompts)
    {
        this.prompts = prompts;
        return this;
    }

    public McpConditionConfigBuilder<T> resources(
        List<String> resources)
    {
        this.resources = resources;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpConditionConfigBuilder<T>> thisType()
    {
        return (Class<McpConditionConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpConditionConfig(toolkit, tools, prompts, resources));
    }
}
