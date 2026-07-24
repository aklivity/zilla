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

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;

public final class McpHttpToolConfigBuilder<T> extends ConfigBuilder<T, McpHttpToolConfigBuilder<T>>
{
    private final Function<McpHttpToolConfig, T> mapper;

    private String name;
    private String summary;
    private String description;
    private ModelConfig input;
    private ModelConfig output;
    private boolean outputMaybeWrapped;

    McpHttpToolConfigBuilder(
        Function<McpHttpToolConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpHttpToolConfigBuilder<T>> thisType()
    {
        return (Class<McpHttpToolConfigBuilder<T>>) getClass();
    }

    public McpHttpToolConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public McpHttpToolConfigBuilder<T> summary(
        String summary)
    {
        this.summary = summary;
        return this;
    }

    public McpHttpToolConfigBuilder<T> description(
        String description)
    {
        this.description = description;
        return this;
    }

    public McpHttpToolConfigBuilder<T> input(
        ModelConfig input)
    {
        this.input = input;
        return this;
    }

    public McpHttpToolConfigBuilder<T> output(
        ModelConfig output)
    {
        this.output = output;
        return this;
    }

    public McpHttpToolConfigBuilder<T> outputMaybeWrapped(
        boolean outputMaybeWrapped)
    {
        this.outputMaybeWrapped = outputMaybeWrapped;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpHttpToolConfig(name, summary, description, input, output, outputMaybeWrapped));
    }
}
