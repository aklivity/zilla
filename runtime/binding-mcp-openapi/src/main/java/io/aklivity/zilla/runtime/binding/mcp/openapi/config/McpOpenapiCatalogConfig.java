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

import java.util.function.Function;

public final class McpOpenapiCatalogConfig
{
    public final String name;
    public final String subject;
    public final String version;

    public static McpOpenapiCatalogConfigBuilder<McpOpenapiCatalogConfig> builder()
    {
        return new McpOpenapiCatalogConfigBuilder<>(McpOpenapiCatalogConfig.class::cast);
    }

    public static <T> McpOpenapiCatalogConfigBuilder<T> builder(
        Function<McpOpenapiCatalogConfig, T> mapper)
    {
        return new McpOpenapiCatalogConfigBuilder<>(mapper);
    }

    McpOpenapiCatalogConfig(
        String name,
        String subject,
        String version)
    {
        this.name = name;
        this.subject = subject;
        this.version = version;
    }
}
