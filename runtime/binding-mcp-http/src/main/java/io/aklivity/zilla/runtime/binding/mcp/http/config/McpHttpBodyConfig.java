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
package io.aklivity.zilla.runtime.binding.mcp.http.config;

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ModelConfig;

public final class McpHttpBodyConfig
{
    public final ModelConfig model;
    public final Map<String, String> template;

    public static McpHttpBodyConfigBuilder<McpHttpBodyConfig> builder()
    {
        return new McpHttpBodyConfigBuilder<>(McpHttpBodyConfig.class::cast);
    }

    public static <T> McpHttpBodyConfigBuilder<T> builder(
        Function<McpHttpBodyConfig, T> mapper)
    {
        return new McpHttpBodyConfigBuilder<>(mapper);
    }

    McpHttpBodyConfig(
        ModelConfig model,
        Map<String, String> template)
    {
        this.model = model;
        this.template = template;
    }
}
