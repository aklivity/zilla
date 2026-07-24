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

public final class ProxySecureInfoConfigBuilder<T> extends ConfigBuilder<T, ProxySecureInfoConfigBuilder<T>>
{
    private final Function<ProxySecureInfoConfig, T> mapper;

    private String version;
    private String cipher;
    private String key;
    private String name;
    private String signature;

    ProxySecureInfoConfigBuilder(
        Function<ProxySecureInfoConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<ProxySecureInfoConfigBuilder<T>> thisType()
    {
        return (Class<ProxySecureInfoConfigBuilder<T>>) getClass();
    }

    public ProxySecureInfoConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public ProxySecureInfoConfigBuilder<T> cipher(
        String cipher)
    {
        this.cipher = cipher;
        return this;
    }

    public ProxySecureInfoConfigBuilder<T> key(
        String key)
    {
        this.key = key;
        return this;
    }

    public ProxySecureInfoConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public ProxySecureInfoConfigBuilder<T> signature(
        String signature)
    {
        this.signature = signature;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new ProxySecureInfoConfig(version, cipher, key, name, signature));
    }
}
