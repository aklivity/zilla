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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpOpenapiSpecificationConfigBuilder<T> extends ConfigBuilder<T, McpOpenapiSpecificationConfigBuilder<T>>
{
    private final Function<McpOpenapiSpecificationConfig, T> mapper;

    private String label;
    private String server;
    private List<McpOpenapiCatalogConfig> catalogs;
    private Map<String, String> security;
    private McpOpenapiCatalogConfig overlay;

    McpOpenapiSpecificationConfigBuilder(
        Function<McpOpenapiSpecificationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOpenapiSpecificationConfigBuilder<T>> thisType()
    {
        return (Class<McpOpenapiSpecificationConfigBuilder<T>>) getClass();
    }

    public McpOpenapiSpecificationConfigBuilder<T> label(
        String label)
    {
        this.label = label;
        return this;
    }

    public McpOpenapiSpecificationConfigBuilder<T> server(
        String server)
    {
        this.server = server;
        return this;
    }

    public McpOpenapiCatalogConfigBuilder<McpOpenapiSpecificationConfigBuilder<T>> catalog()
    {
        return new McpOpenapiCatalogConfigBuilder<>(this::catalog);
    }

    public McpOpenapiSpecificationConfigBuilder<T> catalog(
        McpOpenapiCatalogConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new ArrayList<>();
        }
        catalogs.add(catalog);
        return this;
    }

    public McpOpenapiSpecificationConfigBuilder<T> security(
        Map<String, String> security)
    {
        this.security = security;
        return this;
    }

    public McpOpenapiCatalogConfigBuilder<McpOpenapiSpecificationConfigBuilder<T>> overlay()
    {
        return new McpOpenapiCatalogConfigBuilder<>(this::overlay);
    }

    public McpOpenapiSpecificationConfigBuilder<T> overlay(
        McpOpenapiCatalogConfig overlay)
    {
        this.overlay = overlay;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOpenapiSpecificationConfig(label, server, catalogs, security, overlay));
    }
}
