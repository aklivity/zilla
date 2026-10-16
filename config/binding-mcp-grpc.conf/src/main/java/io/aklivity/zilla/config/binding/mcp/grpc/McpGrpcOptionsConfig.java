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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpGrpcOptionsConfig extends OptionsConfig
{
    public final String server;
    public final McpGrpcCatalogConfig catalog;
    public final McpGrpcAuthorizationConfig authorization;
    public final List<McpGrpcToolConfig> tools;

    public static McpGrpcOptionsConfigBuilder<McpGrpcOptionsConfig> builder()
    {
        return new McpGrpcOptionsConfigBuilder<>(McpGrpcOptionsConfig.class::cast);
    }

    public static <T> McpGrpcOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new McpGrpcOptionsConfigBuilder<>(mapper);
    }

    McpGrpcOptionsConfig(
        String server,
        McpGrpcCatalogConfig catalog,
        McpGrpcAuthorizationConfig authorization,
        List<McpGrpcToolConfig> tools)
    {
        super(null, null);
        this.server = server;
        this.catalog = catalog;
        this.authorization = authorization;
        this.tools = tools;
    }
}
