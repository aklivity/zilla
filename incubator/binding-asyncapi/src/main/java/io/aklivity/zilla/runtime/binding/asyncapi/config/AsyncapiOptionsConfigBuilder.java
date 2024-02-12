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

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

import java.util.List;
import java.util.function.Function;

public final class AsyncapiOptionsConfigBuilder<T> extends ConfigBuilder<T, AsyncapiOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    public List<AsyncapiConfig> specs;
    private String host;
    private int[] ports;
    private List<String> keys;
    private List<String> trust;
    private List<String> sni;
    private List<String> alpn;
    private Boolean trustcacerts;

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

    public AsyncapiOptionsConfigBuilder<T> host(
        String host)
    {
        this.host = host;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> ports(
        int[] ports)
    {
        this.ports = ports;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> keys(
        List<String> keys)
    {
        this.keys = keys;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> trust(
        List<String> trust)
    {
        this.trust = trust;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> sni(
        List<String> sni)
    {
        this.sni = sni;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> alpn(
        List<String> alpn)
    {
        this.alpn = alpn;
        return this;
    }

    public AsyncapiOptionsConfigBuilder<T> trustcacerts(
        boolean trustcacerts)
    {
        this.trustcacerts = trustcacerts;
        return this;
    }

    @Override
    public T build()
    {
        final boolean trustcacerts = this.trustcacerts == null ? this.trust == null : this.trustcacerts;
        return mapper.apply(new AsyncapiOptionsConfig(specs, host, ports, keys, trust, sni, alpn, trustcacerts));
    }
}
