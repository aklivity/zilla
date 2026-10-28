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
package io.aklivity.zilla.config.binding.a2a;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class A2aDiscoveryConfigBuilder<T> extends ConfigBuilder<T, A2aDiscoveryConfigBuilder<T>>
{
    private final Function<A2aDiscoveryConfig, T> mapper;

    private String agent;
    private A2aDiscoverySearchConfig search;

    A2aDiscoveryConfigBuilder(
        Function<A2aDiscoveryConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aDiscoveryConfigBuilder<T>> thisType()
    {
        return (Class<A2aDiscoveryConfigBuilder<T>>) getClass();
    }

    public A2aDiscoveryConfigBuilder<T> agent(
        String agent)
    {
        this.agent = agent;
        return this;
    }

    public A2aDiscoveryConfigBuilder<T> search(
        A2aDiscoverySearchConfig search)
    {
        this.search = search;
        return this;
    }

    public A2aDiscoverySearchConfigBuilder<A2aDiscoveryConfigBuilder<T>> search()
    {
        return A2aDiscoverySearchConfig.builder(this::search);
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aDiscoveryConfig(agent, search));
    }
}
