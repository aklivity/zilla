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

import java.time.Duration;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class A2aCacheConfigBuilder<T> extends ConfigBuilder<T, A2aCacheConfigBuilder<T>>
{
    private final Function<A2aCacheConfig, T> mapper;

    private String store;
    private Duration ttl;
    private A2aAuthorizationConfig authorization;

    A2aCacheConfigBuilder(
        Function<A2aCacheConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aCacheConfigBuilder<T>> thisType()
    {
        return (Class<A2aCacheConfigBuilder<T>>) getClass();
    }

    public A2aCacheConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public A2aCacheConfigBuilder<T> ttl(
        Duration ttl)
    {
        this.ttl = ttl;
        return this;
    }

    public A2aCacheConfigBuilder<T> authorization(
        A2aAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public A2aAuthorizationConfigBuilder<A2aCacheConfigBuilder<T>> authorization()
    {
        return A2aAuthorizationConfig.builder(this::authorization);
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aCacheConfig(store, ttl, authorization));
    }
}
