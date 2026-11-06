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
package io.aklivity.zilla.config.binding.a2a.http;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public final class A2aHttpWithConfigBuilder<T> extends ConfigBuilder<T, A2aHttpWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private Map<String, String> headers;
    private ModelConfig body;

    A2aHttpWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aHttpWithConfigBuilder<T>> thisType()
    {
        return (Class<A2aHttpWithConfigBuilder<T>>) getClass();
    }

    public A2aHttpWithConfigBuilder<T> headers(
        Map<String, String> headers)
    {
        this.headers = headers;
        return this;
    }

    public A2aHttpWithConfigBuilder<T> header(
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

    public A2aHttpWithConfigBuilder<T> body(
        ModelConfig body)
    {
        this.body = body;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aHttpWithConfig(headers, body));
    }
}
