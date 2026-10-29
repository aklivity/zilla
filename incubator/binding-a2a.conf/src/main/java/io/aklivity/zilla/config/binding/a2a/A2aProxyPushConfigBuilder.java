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
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class A2aProxyPushConfigBuilder<T> extends ConfigBuilder<T, A2aProxyPushConfigBuilder<T>>
{
    private final Function<A2aProxyPushConfig, T> mapper;

    private String url;
    private String vault;
    private String key;
    private Duration ttl;
    private int maxLength = -1;
    private List<String> allow;
    private String exit;
    private A2aAuthorizationConfig authorization;

    A2aProxyPushConfigBuilder(
        Function<A2aProxyPushConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aProxyPushConfigBuilder<T>> thisType()
    {
        return (Class<A2aProxyPushConfigBuilder<T>>) getClass();
    }

    public A2aProxyPushConfigBuilder<T> url(
        String url)
    {
        this.url = url;
        return this;
    }

    public A2aProxyPushConfigBuilder<T> vault(
        String vault)
    {
        this.vault = vault;
        return this;
    }

    public A2aProxyPushConfigBuilder<T> key(
        String key)
    {
        this.key = key;
        return this;
    }

    public A2aProxyPushConfigBuilder<T> ttl(
        Duration ttl)
    {
        this.ttl = ttl;
        return this;
    }

    public A2aProxyPushConfigBuilder<T> maxLength(
        int maxLength)
    {
        this.maxLength = maxLength;
        return this;
    }

    public A2aProxyPushConfigBuilder<T> allow(
        List<String> allow)
    {
        this.allow = allow;
        return this;
    }

    public A2aProxyPushConfigBuilder<T> exit(
        String exit)
    {
        this.exit = exit;
        return this;
    }

    public A2aProxyPushConfigBuilder<T> authorization(
        A2aAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public A2aAuthorizationConfigBuilder<A2aProxyPushConfigBuilder<T>> authorization()
    {
        return A2aAuthorizationConfig.builder(this::authorization);
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aProxyPushConfig(url, vault, key, ttl, maxLength, allow, exit, authorization));
    }
}
