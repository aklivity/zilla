/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfigBuilder;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class OpenpaiOptionsConfigBuilder<T> extends ConfigBuilder<T, OpenpaiOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private HttpAuthorizationConfig authorization;
    private List<OpenapiConfig> openapis;
    private TlsOptionsConfig tls;

    OpenpaiOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenpaiOptionsConfigBuilder<T>> thisType()
    {
        return (Class<OpenpaiOptionsConfigBuilder<T>>) getClass();
    }

    public OpenpaiOptionsConfigBuilder<T> tls(
        TlsOptionsConfig tls)
    {
        this.tls = tls;
        return this;
    }

    public OpenpaiOptionsConfigBuilder<T> openapi(
        OpenapiConfig openapi)
    {
        if (openapis == null)
        {
            openapis = new ArrayList<>();
        }
        openapis.add(openapi);
        return this;
    }

    public HttpAuthorizationConfigBuilder<HttpAuthorizationConfig> authorization()
    {
        return HttpAuthorizationConfigBuilder.builder(this::authorization);
    }

    @Override
    public T build()
    {
        return mapper.apply(new OpenapiOptionsConfig(tls, authorization, openapis));
    }

    private OpenpaiOptionsConfigBuilder<T> authorization(
        HttpAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }
}
