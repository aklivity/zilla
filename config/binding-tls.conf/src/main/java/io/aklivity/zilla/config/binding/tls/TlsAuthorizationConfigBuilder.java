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
package io.aklivity.zilla.config.binding.tls;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public class TlsAuthorizationConfigBuilder<T> extends ConfigBuilder<T, TlsAuthorizationConfigBuilder<T>>
{
    private final Function<TlsAuthorizationConfig, T> mapper;

    private String name;
    private TlsCredentialsConfig credentials;

    TlsAuthorizationConfigBuilder(
        Function<TlsAuthorizationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TlsAuthorizationConfigBuilder<T>> thisType()
    {
        return (Class<TlsAuthorizationConfigBuilder<T>>) getClass();
    }

    public TlsAuthorizationConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public TlsCredentialsConfigBuilder<TlsAuthorizationConfigBuilder<T>> credentials()
    {
        return new TlsCredentialsConfigBuilder<>(this::credentials);
    }

    public TlsAuthorizationConfigBuilder<T> credentials(
        TlsCredentialsConfig credentials)
    {
        this.credentials = credentials;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TlsAuthorizationConfig(name, credentials));
    }
}
