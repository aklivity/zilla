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
package io.aklivity.zilla.runtime.binding.mcp.config;

import static java.util.function.Function.identity;

import java.time.Duration;
import java.util.function.Function;

public final class McpCacheConfig
{
    public final String store;
    public final Duration ttl;
    public final McpAuthorizationConfig authorization;
    public final McpCacheToolsConfig tools;

    McpCacheConfig(
        String store,
        Duration ttl,
        McpAuthorizationConfig authorization,
        McpCacheToolsConfig tools)
    {
        this.store = store;
        this.ttl = ttl;
        this.authorization = authorization;
        this.tools = tools;
    }

    public static McpCacheConfigBuilder<McpCacheConfig> builder()
    {
        return new McpCacheConfigBuilder<>(identity());
    }

    public static <T> McpCacheConfigBuilder<T> builder(
        Function<McpCacheConfig, T> mapper)
    {
        return new McpCacheConfigBuilder<>(mapper);
    }
}
