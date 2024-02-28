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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class AsyncapiOptionsConfigBuilder<T> extends ConfigBuilder<T, AsyncapiOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    public List<AsyncapiConfig> specs;
    private TcpOptionsConfig tcp;
    private TlsOptionsConfig tls;
    private HttpOptionsConfig http;
    private KafkaOptionsConfig kafka;

    AsyncapiOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiOptionsConfigBuilder<T>> thisType()
    {
        return (Class<AsyncapiOptionsConfigBuilder<T>>) getClass();
    }

    public AsyncapiOptionsConfigBuilder<T> specs(
        List<AsyncapiConfig> specs)
    {
        this.specs = specs;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> tcp(
        TcpOptionsConfig tcp)
    {
        this.tcp = tcp;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> tls(
        TlsOptionsConfig tls)
    {
        this.tls = tls;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> http(
        HttpOptionsConfig http)
    {
        this.http = http;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> kafka(
        KafkaOptionsConfig kafka)
    {
        this.kafka = kafka;
        return this;
    }


    @Override
    public T build()
    {
        return mapper.apply(new AsyncapiOptionsConfig(specs, tcp, tls, http, kafka));
    }
}
