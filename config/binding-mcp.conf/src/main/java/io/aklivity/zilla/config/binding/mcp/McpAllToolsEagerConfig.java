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

import java.util.function.Function;

public final class McpAllToolsEagerConfig extends McpToolsEagerConfig
{
    public static final String NAME = "all";

    public static McpAllToolsEagerConfigBuilder<McpAllToolsEagerConfig> builder()
    {
        return new McpAllToolsEagerConfigBuilder<>(McpAllToolsEagerConfig.class::cast);
    }

    public static <T> McpAllToolsEagerConfigBuilder<T> builder(
        Function<McpAllToolsEagerConfig, T> mapper)
    {
        return new McpAllToolsEagerConfigBuilder<>(mapper);
    }

    McpAllToolsEagerConfig()
    {
        super(NAME);
    }
}
