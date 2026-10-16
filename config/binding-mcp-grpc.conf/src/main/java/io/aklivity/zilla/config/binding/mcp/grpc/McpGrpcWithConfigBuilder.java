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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class McpGrpcWithConfigBuilder<T> extends ConfigBuilder<T, McpGrpcWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private String method;
    private Map<String, JsonValue> option;

    McpGrpcWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpGrpcWithConfigBuilder<T>> thisType()
    {
        return (Class<McpGrpcWithConfigBuilder<T>>) getClass();
    }

    public McpGrpcWithConfigBuilder<T> method(
        String method)
    {
        this.method = method;
        return this;
    }

    public McpGrpcWithConfigBuilder<T> option(
        String name,
        JsonValue value)
    {
        if (option == null)
        {
            option = new LinkedHashMap<>();
        }
        option.put(name, value);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpGrpcWithConfig(method, option));
    }
}
