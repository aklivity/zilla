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
package io.aklivity.zilla.runtime.binding.mcp.openapi.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class McpOpenapiOptionsConfig extends OptionsConfig
{
    public final McpOpenapiAuthorizationConfig authorization;
    public final List<McpOpenapiSpecificationConfig> specs;
    public final List<McpOpenapiToolConfig> tools;
    public final List<McpOpenapiResourceConfig> resources;

    public static McpOpenapiOptionsConfigBuilder<McpOpenapiOptionsConfig> builder()
    {
        return new McpOpenapiOptionsConfigBuilder<>(McpOpenapiOptionsConfig.class::cast);
    }

    public static <T> McpOpenapiOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new McpOpenapiOptionsConfigBuilder<>(mapper);
    }

    McpOpenapiOptionsConfig(
        McpOpenapiAuthorizationConfig authorization,
        List<McpOpenapiSpecificationConfig> specs,
        List<McpOpenapiToolConfig> tools,
        List<McpOpenapiResourceConfig> resources)
    {
        this.authorization = authorization;
        this.specs = specs;
        this.tools = tools;
        this.resources = resources;
    }
}
