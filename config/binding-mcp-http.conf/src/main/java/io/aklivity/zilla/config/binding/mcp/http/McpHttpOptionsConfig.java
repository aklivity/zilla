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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpHttpOptionsConfig extends OptionsConfig
{
    public final McpHttpAuthorizationConfig authorization;
    public final List<McpHttpToolConfig> tools;
    public final List<McpHttpResourceConfig> resources;

    public static McpHttpOptionsConfigBuilder<McpHttpOptionsConfig> builder()
    {
        return new McpHttpOptionsConfigBuilder<>(McpHttpOptionsConfig.class::cast);
    }

    public static <T> McpHttpOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new McpHttpOptionsConfigBuilder<>(mapper);
    }

    McpHttpOptionsConfig(
        McpHttpAuthorizationConfig authorization,
        List<McpHttpToolConfig> tools,
        List<McpHttpResourceConfig> resources)
    {
        this.authorization = authorization;
        this.tools = tools;
        this.resources = resources;
    }
}
