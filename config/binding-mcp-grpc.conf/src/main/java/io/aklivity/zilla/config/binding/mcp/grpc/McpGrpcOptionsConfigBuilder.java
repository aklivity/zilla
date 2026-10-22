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
package io.aklivity.zilla.config.binding.mcp.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpGrpcOptionsConfigBuilder<T> extends ConfigBuilder<T, McpGrpcOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String server;
    private McpGrpcCatalogConfig catalog;
    private McpGrpcAuthorizationConfig authorization;
    private List<McpGrpcToolConfig> tools;

    McpGrpcOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpGrpcOptionsConfigBuilder<T>> thisType()
    {
        return (Class<McpGrpcOptionsConfigBuilder<T>>) getClass();
    }

    public McpGrpcOptionsConfigBuilder<T> server(
        String server)
    {
        this.server = server;
        return this;
    }

    public McpGrpcCatalogConfigBuilder<McpGrpcOptionsConfigBuilder<T>> catalog()
    {
        return new McpGrpcCatalogConfigBuilder<>(this::catalog);
    }

    public McpGrpcOptionsConfigBuilder<T> catalog(
        McpGrpcCatalogConfig catalog)
    {
        this.catalog = catalog;
        return this;
    }

    public McpGrpcOptionsConfigBuilder<T> authorization(
        McpGrpcAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public McpGrpcToolConfigBuilder<McpGrpcOptionsConfigBuilder<T>> tool()
    {
        return new McpGrpcToolConfigBuilder<>(this::tool);
    }

    public McpGrpcOptionsConfigBuilder<T> tool(
        McpGrpcToolConfig tool)
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
        return mapper.apply(new McpGrpcOptionsConfig(server, catalog, authorization, tools));
    }
}
