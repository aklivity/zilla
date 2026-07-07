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
package io.aklivity.zilla.runtime.binding.mcp.openapi.config;

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class McpOpenapiWithConfigBuilder<T> extends ConfigBuilder<T, McpOpenapiWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private String spec;
    private String operation;
    private String tag;
    private Map<String, String> params;
    private Map<String, String> body;

    McpOpenapiWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOpenapiWithConfigBuilder<T>> thisType()
    {
        return (Class<McpOpenapiWithConfigBuilder<T>>) getClass();
    }

    public McpOpenapiWithConfigBuilder<T> spec(
        String spec)
    {
        this.spec = spec;
        return this;
    }

    public McpOpenapiWithConfigBuilder<T> operation(
        String operation)
    {
        this.operation = operation;
        return this;
    }

    public McpOpenapiWithConfigBuilder<T> tag(
        String tag)
    {
        this.tag = tag;
        return this;
    }

    public McpOpenapiWithConfigBuilder<T> params(
        Map<String, String> params)
    {
        this.params = params;
        return this;
    }

    public McpOpenapiWithConfigBuilder<T> body(
        Map<String, String> body)
    {
        this.body = body;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOpenapiWithConfig(spec, operation, tag, params, body));
    }
}
