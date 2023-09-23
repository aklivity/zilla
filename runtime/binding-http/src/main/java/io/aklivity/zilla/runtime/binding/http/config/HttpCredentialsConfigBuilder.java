/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpCredentialsConfigBuilder<T> extends ConfigBuilder<T, HttpCredentialsConfigBuilder<T>>
{
    private final Function<HttpCredentialsConfig, T> mapper;

    private List<HttpPatternConfig> headers;
    private List<HttpPatternConfig> parameters;
    private List<HttpPatternConfig> cookies;

    HttpCredentialsConfigBuilder(
        Function<HttpCredentialsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpCredentialsConfigBuilder<T>> thisType()
    {
        return (Class<HttpCredentialsConfigBuilder<T>>) getClass();
    }

    public HttpPatternConfigBuilder<HttpCredentialsConfigBuilder<T>> header()
    {
        return new HttpPatternConfigBuilder<>(this::header);
    }

    public HttpPatternConfigBuilder<HttpCredentialsConfigBuilder<T>> parameter()
    {
        return new HttpPatternConfigBuilder<>(this::parameter);
    }

    public HttpPatternConfigBuilder<HttpCredentialsConfigBuilder<T>> cookie()
    {
        return new HttpPatternConfigBuilder<>(this::cookie);
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpCredentialsConfig(headers, parameters, cookies));
    }

    private HttpCredentialsConfigBuilder<T> header(
        HttpPatternConfig header)
    {
        if (headers == null)
        {
            headers = new LinkedList<>();
        }
        headers.add(header);
        return this;
    }

    private HttpCredentialsConfigBuilder<T> parameter(
        HttpPatternConfig parameter)
    {
        if (parameters == null)
        {
            parameters = new LinkedList<>();
        }
        parameters.add(parameter);
        return this;
    }

    private HttpCredentialsConfigBuilder<T> cookie(
        HttpPatternConfig cookie)
    {
        if (cookies == null)
        {
            cookies = new LinkedList<>();
        }
        cookies.add(cookie);
        return this;
    }
}
