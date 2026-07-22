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
package io.aklivity.zilla.runtime.binding.mcp.http.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class McpHttpResourceConfigBuilder<T> extends ConfigBuilder<T, McpHttpResourceConfigBuilder<T>>
{
    private final Function<McpHttpResourceConfig, T> mapper;

    private String name;
    private String uri;
    private boolean template;
    private String description;
    private String mimeType;
    private ModelConfig output;

    McpHttpResourceConfigBuilder(
        Function<McpHttpResourceConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpHttpResourceConfigBuilder<T>> thisType()
    {
        return (Class<McpHttpResourceConfigBuilder<T>>) getClass();
    }

    public McpHttpResourceConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public McpHttpResourceConfigBuilder<T> uri(
        String uri)
    {
        this.uri = uri;
        return this;
    }

    public McpHttpResourceConfigBuilder<T> template(
        boolean template)
    {
        this.template = template;
        return this;
    }

    public McpHttpResourceConfigBuilder<T> description(
        String description)
    {
        this.description = description;
        return this;
    }

    public McpHttpResourceConfigBuilder<T> mimeType(
        String mimeType)
    {
        this.mimeType = mimeType;
        return this;
    }

    public McpHttpResourceConfigBuilder<T> output(
        ModelConfig output)
    {
        this.output = output;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpHttpResourceConfig(name, uri, template, description, mimeType, output));
    }
}
