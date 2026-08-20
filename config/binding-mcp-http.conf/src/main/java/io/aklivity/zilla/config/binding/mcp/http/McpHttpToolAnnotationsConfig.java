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
package io.aklivity.zilla.config.binding.mcp.http;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class McpHttpToolAnnotationsConfig extends Config
{
    public final Boolean readOnlyHint;
    public final Boolean destructiveHint;
    public final Boolean idempotentHint;
    public final Boolean openWorldHint;

    public static McpHttpToolAnnotationsConfigBuilder<McpHttpToolAnnotationsConfig> builder()
    {
        return new McpHttpToolAnnotationsConfigBuilder<>(McpHttpToolAnnotationsConfig.class::cast);
    }

    public static <T> McpHttpToolAnnotationsConfigBuilder<T> builder(
        Function<McpHttpToolAnnotationsConfig, T> mapper)
    {
        return new McpHttpToolAnnotationsConfigBuilder<>(mapper);
    }

    McpHttpToolAnnotationsConfig(
        Boolean readOnlyHint,
        Boolean destructiveHint,
        Boolean idempotentHint,
        Boolean openWorldHint)
    {
        this.readOnlyHint = readOnlyHint;
        this.destructiveHint = destructiveHint;
        this.idempotentHint = idempotentHint;
        this.openWorldHint = openWorldHint;
    }
}
