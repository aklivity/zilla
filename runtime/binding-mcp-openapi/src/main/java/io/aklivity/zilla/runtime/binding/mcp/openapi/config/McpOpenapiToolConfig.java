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

import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class McpOpenapiToolConfig
{
    public final String name;
    public final String description;
    public final ModelConfig output;

    public static McpOpenapiToolConfigBuilder<McpOpenapiToolConfig> builder()
    {
        return new McpOpenapiToolConfigBuilder<>(McpOpenapiToolConfig.class::cast);
    }

    public static <T> McpOpenapiToolConfigBuilder<T> builder(
        Function<McpOpenapiToolConfig, T> mapper)
    {
        return new McpOpenapiToolConfigBuilder<>(mapper);
    }

    McpOpenapiToolConfig(
        String name,
        String description,
        ModelConfig output)
    {
        this.name = name;
        this.description = description;
        this.output = output;
    }
}
