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
package io.aklivity.zilla.config.binding.mcp;

import static java.util.function.Function.identity;

import java.util.function.Function;

public final class McpWithCacheConfig
{
    public final String credentials;

    McpWithCacheConfig(
        String credentials)
    {
        this.credentials = credentials;
    }

    public static McpWithCacheConfigBuilder<McpWithCacheConfig> builder()
    {
        return new McpWithCacheConfigBuilder<>(identity());
    }

    public static <T> McpWithCacheConfigBuilder<T> builder(
        Function<McpWithCacheConfig, T> mapper)
    {
        return new McpWithCacheConfigBuilder<>(mapper);
    }
}
