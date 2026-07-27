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
package io.aklivity.zilla.config.binding.proxy;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class ProxyAddressConfigBuilder<T> extends ConfigBuilder<T, ProxyAddressConfigBuilder<T>>
{
    private final Function<ProxyAddressConfig, T> mapper;

    private String host;
    private Integer port;

    ProxyAddressConfigBuilder(
        Function<ProxyAddressConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<ProxyAddressConfigBuilder<T>> thisType()
    {
        return (Class<ProxyAddressConfigBuilder<T>>) getClass();
    }

    public ProxyAddressConfigBuilder<T> host(
        String host)
    {
        this.host = host;
        return this;
    }

    public ProxyAddressConfigBuilder<T> port(
        Integer port)
    {
        this.port = port;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new ProxyAddressConfig(host, port));
    }
}
