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

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpAuthorizationConfigBuilder<T> extends ConfigBuilder<T, HttpAuthorizationConfigBuilder<T>>
{
    private final Function<HttpAuthorizationConfig, T> mapper;

    private String name;
    private HttpCredentialsConfig credentials;

    HttpAuthorizationConfigBuilder(
        Function<HttpAuthorizationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpAuthorizationConfigBuilder<T>> thisType()
    {
        return (Class<HttpAuthorizationConfigBuilder<T>>) getClass();
    }

    public HttpAuthorizationConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public HttpCredentialsConfigBuilder<HttpAuthorizationConfigBuilder<T>> credentials()
    {
        return new HttpCredentialsConfigBuilder<>(this::credentials);
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpAuthorizationConfig(name, credentials));
    }

    private HttpAuthorizationConfigBuilder<T> credentials(
        HttpCredentialsConfig credentials)
    {
        this.credentials = credentials;
        return this;
    }
}
