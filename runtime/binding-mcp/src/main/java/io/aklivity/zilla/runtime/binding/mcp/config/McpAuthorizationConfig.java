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

import java.util.function.Function;

public final class McpAuthorizationConfig
{
    public final String name;
    public final String credentials;

    public transient String qname;

    public static McpAuthorizationConfigBuilder<McpAuthorizationConfig> builder()
    {
        return new McpAuthorizationConfigBuilder<>(identity());
    }

    public static <T> McpAuthorizationConfigBuilder<T> builder(
        Function<McpAuthorizationConfig, T> mapper)
    {
        return new McpAuthorizationConfigBuilder<>(mapper);
    }

    McpAuthorizationConfig(
        String name,
        String credentials)
    {
        this.name = name;
        this.credentials = credentials;
    }
}
