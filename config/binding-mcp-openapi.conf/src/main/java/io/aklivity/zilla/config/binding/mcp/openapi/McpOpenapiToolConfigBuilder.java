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
package io.aklivity.zilla.config.binding.mcp.openapi;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;

public final class McpOpenapiToolConfigBuilder<T> extends ConfigBuilder<T, McpOpenapiToolConfigBuilder<T>>
{
    private final Function<McpOpenapiToolConfig, T> mapper;

    private String name;
    private String description;
    private String summary;
    private ModelConfig input;
    private ModelConfig output;

    McpOpenapiToolConfigBuilder(
        Function<McpOpenapiToolConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOpenapiToolConfigBuilder<T>> thisType()
    {
        return (Class<McpOpenapiToolConfigBuilder<T>>) getClass();
    }

    public McpOpenapiToolConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public McpOpenapiToolConfigBuilder<T> description(
        String description)
    {
        this.description = description;
        return this;
    }

    public McpOpenapiToolConfigBuilder<T> summary(
        String summary)
    {
        this.summary = summary;
        return this;
    }

    public McpOpenapiToolConfigBuilder<T> input(
        ModelConfig input)
    {
        this.input = input;
        return this;
    }

    public McpOpenapiToolConfigBuilder<T> output(
        ModelConfig output)
    {
        this.output = output;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOpenapiToolConfig(name, description, summary, input, output));
    }
}
