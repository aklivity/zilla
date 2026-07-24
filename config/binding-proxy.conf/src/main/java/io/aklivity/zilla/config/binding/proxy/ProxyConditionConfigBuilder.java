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

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class ProxyConditionConfigBuilder<T> extends ConfigBuilder<T, ProxyConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String transport;
    private String family;
    private ProxyAddressConfig source;
    private ProxyAddressConfig destination;
    private ProxyInfoConfig info;

    ProxyConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<ProxyConditionConfigBuilder<T>> thisType()
    {
        return (Class<ProxyConditionConfigBuilder<T>>) getClass();
    }

    public ProxyConditionConfigBuilder<T> transport(
        String transport)
    {
        this.transport = transport;
        return this;
    }

    public ProxyConditionConfigBuilder<T> family(
        String family)
    {
        this.family = family;
        return this;
    }

    public ProxyConditionConfigBuilder<T> source(
        ProxyAddressConfig source)
    {
        this.source = source;
        return this;
    }

    public ProxyConditionConfigBuilder<T> destination(
        ProxyAddressConfig destination)
    {
        this.destination = destination;
        return this;
    }

    public ProxyConditionConfigBuilder<T> info(
        ProxyInfoConfig info)
    {
        this.info = info;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new ProxyConditionConfig(transport, family, source, destination, info));
    }
}
