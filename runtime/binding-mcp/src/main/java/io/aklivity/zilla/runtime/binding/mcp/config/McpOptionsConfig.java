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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class McpOptionsConfig extends OptionsConfig
{
    public final McpElicitationConfig elicitation;
    public final McpAuthorizationConfig authorization;
    public final McpCacheConfig cache;
    public final String server;
    public final ModelConfig tools;

    McpOptionsConfig(
        McpElicitationConfig elicitation,
        McpAuthorizationConfig authorization,
        McpCacheConfig cache,
        String server,
        ModelConfig tools)
    {
        super(tools != null ? List.of(tools) : List.of(), List.of());
        this.elicitation = elicitation;
        this.authorization = authorization;
        this.cache = cache;
        this.server = server;
        this.tools = tools;
    }

    public static McpOptionsConfigBuilder<McpOptionsConfig> builder()
    {
        return new McpOptionsConfigBuilder<>(McpOptionsConfig.class::cast);
    }

    public static <T> McpOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new McpOptionsConfigBuilder<>(mapper);
    }
}
