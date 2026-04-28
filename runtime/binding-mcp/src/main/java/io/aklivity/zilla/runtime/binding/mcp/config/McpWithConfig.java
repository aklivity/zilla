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

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class McpWithConfig extends WithConfig
{
    public final Map<String, String> headers;

    public McpWithConfig(
        Map<String, String> headers)
    {
        this.headers = headers;
    }

    public static McpWithConfigBuilder<McpWithConfig> builder()
    {
        return new McpWithConfigBuilder<>(McpWithConfig.class::cast);
    }

    public static <T> McpWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new McpWithConfigBuilder<>(mapper);
    }
}
