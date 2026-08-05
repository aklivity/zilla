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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public final class McpHttpWithConfigBuilder<T> extends ConfigBuilder<T, McpHttpWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private Map<String, String> headers;
    private Map<String, String> cookies;
    private ModelConfig query;
    private McpHttpBodyConfig body;

    McpHttpWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpHttpWithConfigBuilder<T>> thisType()
    {
        return (Class<McpHttpWithConfigBuilder<T>>) getClass();
    }

    public McpHttpWithConfigBuilder<T> headers(
        Map<String, String> headers)
    {
        this.headers = headers;
        return this;
    }

    public McpHttpWithConfigBuilder<T> header(
        String name,
        String value)
    {
        if (headers == null)
        {
            headers = new LinkedHashMap<>();
        }
        headers.put(name, value);
        return this;
    }

    public McpHttpWithConfigBuilder<T> cookies(
        Map<String, String> cookies)
    {
        this.cookies = cookies;
        return this;
    }

    public McpHttpWithConfigBuilder<T> cookie(
        String name,
        String value)
    {
        if (cookies == null)
        {
            cookies = new LinkedHashMap<>();
        }
        cookies.put(name, value);
        return this;
    }

    public McpHttpWithConfigBuilder<T> query(
        ModelConfig query)
    {
        this.query = query;
        return this;
    }

    public McpHttpWithConfigBuilder<T> body(
        McpHttpBodyConfig body)
    {
        this.body = body;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpHttpWithConfig(headers, cookies, query, body));
    }
}
