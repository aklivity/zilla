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

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class OpenpaiOptionsConfigBuilder<T> extends ConfigBuilder<T, OpenpaiOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private List<String> servers;
    private TcpOptionsConfig tcp;
    private TlsOptionsConfig tls;
    private HttpOptionsConfig http;
    private List<OpenapiConfig> openapis;

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

    public OpenpaiOptionsConfigBuilder<T> server(
        String server)
    {
        if (servers == null)
        {
            this.servers = new ArrayList<>();
        }
        servers.add(server);

        return this;
    }

    public OpenpaiOptionsConfigBuilder<T> tcp(
        TcpOptionsConfig tcp)
    {
        this.tcp = tcp;
        return this;
    }

    public OpenpaiOptionsConfigBuilder<T> tls(
        TlsOptionsConfig tls)
    {
        this.tls = tls;
        return this;
    }

    public OpenpaiOptionsConfigBuilder<T> http(
        HttpOptionsConfig http)
    {
        this.http = http;
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

    @Override
    public T build()
    {
        return mapper.apply(new OpenapiOptionsConfig(servers, tcp, tls, http, openapis));
    }
}
