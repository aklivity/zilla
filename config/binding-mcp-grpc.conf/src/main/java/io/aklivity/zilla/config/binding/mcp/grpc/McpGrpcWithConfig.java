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
package io.aklivity.zilla.config.binding.mcp.grpc;

import java.util.Map;
import java.util.function.Function;

import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.WithConfig;

public final class McpGrpcWithConfig extends WithConfig
{
    public final String method;
    public final Map<String, JsonValue> option;

    public static McpGrpcWithConfigBuilder<McpGrpcWithConfig> builder()
    {
        return new McpGrpcWithConfigBuilder<>(McpGrpcWithConfig.class::cast);
    }

    public static <T> McpGrpcWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new McpGrpcWithConfigBuilder<>(mapper);
    }

    McpGrpcWithConfig(
        String method,
        Map<String, JsonValue> option)
    {
        this.method = method;
        this.option = option;
    }
}
