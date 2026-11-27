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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aOptionsConfigBuilder<T> extends ConfigBuilder<T, A2aOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String url;
    private List<String> versions;
    private A2aServerPushConfig serverPush;
    private A2aAuthorizationConfig authorization;
    private String server;
    private A2aCacheConfig cache;
    private A2aDiscoveryConfig discovery;
    private A2aProxyPushConfig proxyPush;
    private A2aClientPushConfig clientPush;

    public A2aOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aOptionsConfigBuilder<T>> thisType()
    {
        return (Class<A2aOptionsConfigBuilder<T>>) getClass();
    }

    public A2aOptionsConfigBuilder<T> url(
        String url)
    {
        this.url = url;
        return this;
    }

    public A2aOptionsConfigBuilder<T> versions(
        List<String> versions)
    {
        this.versions = versions;
        return this;
    }

    public A2aOptionsConfigBuilder<T> serverPush(
        A2aServerPushConfig serverPush)
    {
        this.serverPush = serverPush;
        return this;
    }

    public A2aServerPushConfigBuilder<A2aOptionsConfigBuilder<T>> serverPush()
    {
        return A2aServerPushConfig.builder(this::serverPush);
    }

    public A2aOptionsConfigBuilder<T> authorization(
        A2aAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public A2aAuthorizationConfigBuilder<A2aOptionsConfigBuilder<T>> authorization()
    {
        return A2aAuthorizationConfig.builder(this::authorization);
    }

    public A2aOptionsConfigBuilder<T> server(
        String server)
    {
        this.server = server;
        return this;
    }

    public A2aOptionsConfigBuilder<T> cache(
        A2aCacheConfig cache)
    {
        this.cache = cache;
        return this;
    }

    public A2aCacheConfigBuilder<A2aOptionsConfigBuilder<T>> cache()
    {
        return A2aCacheConfig.builder(this::cache);
    }

    public A2aOptionsConfigBuilder<T> discovery(
        A2aDiscoveryConfig discovery)
    {
        this.discovery = discovery;
        return this;
    }

    public A2aDiscoveryConfigBuilder<A2aOptionsConfigBuilder<T>> discovery()
    {
        return A2aDiscoveryConfig.builder(this::discovery);
    }

    public A2aOptionsConfigBuilder<T> proxyPush(
        A2aProxyPushConfig proxyPush)
    {
        this.proxyPush = proxyPush;
        return this;
    }

    public A2aProxyPushConfigBuilder<A2aOptionsConfigBuilder<T>> proxyPush()
    {
        return A2aProxyPushConfig.builder(this::proxyPush);
    }

    public A2aOptionsConfigBuilder<T> clientPush(
        A2aClientPushConfig clientPush)
    {
        this.clientPush = clientPush;
        return this;
    }

    public A2aClientPushConfigBuilder<A2aOptionsConfigBuilder<T>> clientPush()
    {
        return A2aClientPushConfig.builder(this::clientPush);
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aOptionsConfig(url, versions, serverPush, authorization, server, cache, discovery, proxyPush,
            clientPush));
    }
}
