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

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class McpOpenapiResourceConfigBuilder<T> extends ConfigBuilder<T, McpOpenapiResourceConfigBuilder<T>>
{
    private final Function<McpOpenapiResourceConfig, T> mapper;

    private String uri;
    private String description;
    private String mimeType;
    private ModelConfig output;

    McpOpenapiResourceConfigBuilder(
        Function<McpOpenapiResourceConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOpenapiResourceConfigBuilder<T>> thisType()
    {
        return (Class<McpOpenapiResourceConfigBuilder<T>>) getClass();
    }

    public McpOpenapiResourceConfigBuilder<T> uri(
        String uri)
    {
        this.uri = uri;
        return this;
    }

    public McpOpenapiResourceConfigBuilder<T> description(
        String description)
    {
        this.description = description;
        return this;
    }

    public McpOpenapiResourceConfigBuilder<T> mimeType(
        String mimeType)
    {
        this.mimeType = mimeType;
        return this;
    }

    public McpOpenapiResourceConfigBuilder<T> output(
        ModelConfig output)
    {
        this.output = output;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOpenapiResourceConfig(uri, description, mimeType, output));
    }
}
