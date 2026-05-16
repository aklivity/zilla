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
package io.aklivity.zilla.runtime.binding.mcp.config;

import static java.util.function.Function.identity;

import java.time.Duration;
import java.util.function.Function;

public final class McpCacheTtlConfig
{
    public final Duration tools;
    public final Duration resources;
    public final Duration prompts;

    McpCacheTtlConfig(
        Duration tools,
        Duration resources,
        Duration prompts)
    {
        this.tools = tools;
        this.resources = resources;
        this.prompts = prompts;
    }

    public static McpCacheTtlConfigBuilder<McpCacheTtlConfig> builder()
    {
        return new McpCacheTtlConfigBuilder<>(identity());
    }

    public static <T> McpCacheTtlConfigBuilder<T> builder(
        Function<McpCacheTtlConfig, T> mapper)
    {
        return new McpCacheTtlConfigBuilder<>(mapper);
    }
}
