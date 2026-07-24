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

public final class ProxyInfoConfigBuilder<T> extends ConfigBuilder<T, ProxyInfoConfigBuilder<T>>
{
    private final Function<ProxyInfoConfig, T> mapper;

    private String alpn;
    private String authority;
    private byte[] identity;
    private String namespace;
    private ProxySecureInfoConfig secure;

    ProxyInfoConfigBuilder(
        Function<ProxyInfoConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<ProxyInfoConfigBuilder<T>> thisType()
    {
        return (Class<ProxyInfoConfigBuilder<T>>) getClass();
    }

    public ProxyInfoConfigBuilder<T> alpn(
        String alpn)
    {
        this.alpn = alpn;
        return this;
    }

    public ProxyInfoConfigBuilder<T> authority(
        String authority)
    {
        this.authority = authority;
        return this;
    }

    public ProxyInfoConfigBuilder<T> identity(
        byte[] identity)
    {
        this.identity = identity;
        return this;
    }

    public ProxyInfoConfigBuilder<T> namespace(
        String namespace)
    {
        this.namespace = namespace;
        return this;
    }

    public ProxyInfoConfigBuilder<T> secure(
        ProxySecureInfoConfig secure)
    {
        this.secure = secure;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new ProxyInfoConfig(alpn, authority, identity, namespace, secure));
    }
}
