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
package io.aklivity.zilla.runtime.binding.tls.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class TlsConditionConfigBuilder<T> extends ConfigBuilder<T, TlsConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String authority;
    private String alpn;
    private int[] ports;

    TlsConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TlsConditionConfigBuilder<T>> thisType()
    {
        return (Class<TlsConditionConfigBuilder<T>>) getClass();
    }

    public TlsConditionConfigBuilder<T> authority(
        String authority)
    {
        this.authority = authority;
        return this;
    }

    public TlsConditionConfigBuilder<T> alpn(
        String alpn)
    {
        this.alpn = alpn;
        return this;
    }

    public TlsConditionConfigBuilder<T> ports(
        int[] ports)
    {
        this.ports = ports;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TlsConditionConfig(authority, alpn, ports));
    }
}
