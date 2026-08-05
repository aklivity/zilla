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

public final class McpOpenapiCatalogConfigBuilder<T> extends ConfigBuilder<T, McpOpenapiCatalogConfigBuilder<T>>
{
    private final Function<McpOpenapiCatalogConfig, T> mapper;

    private String name;
    private String subject;
    private String version;

    McpOpenapiCatalogConfigBuilder(
        Function<McpOpenapiCatalogConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOpenapiCatalogConfigBuilder<T>> thisType()
    {
        return (Class<McpOpenapiCatalogConfigBuilder<T>>) getClass();
    }

    public McpOpenapiCatalogConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public McpOpenapiCatalogConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public McpOpenapiCatalogConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOpenapiCatalogConfig(name, subject, version));
    }
}
