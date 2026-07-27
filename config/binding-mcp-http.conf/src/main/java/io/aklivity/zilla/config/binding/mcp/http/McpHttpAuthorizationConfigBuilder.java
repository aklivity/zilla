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

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpHttpAuthorizationConfigBuilder<T> extends ConfigBuilder<T, McpHttpAuthorizationConfigBuilder<T>>
{
    private final Function<McpHttpAuthorizationConfig, T> mapper;

    private String name;
    private Map<String, String> headers;

    McpHttpAuthorizationConfigBuilder(
        Function<McpHttpAuthorizationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpHttpAuthorizationConfigBuilder<T>> thisType()
    {
        return (Class<McpHttpAuthorizationConfigBuilder<T>>) getClass();
    }

    public McpHttpAuthorizationConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public McpHttpAuthorizationConfigBuilder<T> headers(
        Map<String, String> headers)
    {
        this.headers = headers;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpHttpAuthorizationConfig(name, headers));
    }
}
