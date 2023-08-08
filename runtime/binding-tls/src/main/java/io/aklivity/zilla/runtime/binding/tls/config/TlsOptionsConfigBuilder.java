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

import static io.aklivity.zilla.runtime.binding.tls.config.TlsMutualConfig.REQUIRED;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class TlsOptionsConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<TlsOptionsConfig, T> mapper;

    private String version;
    private List<String> keys;
    private List<String> trust;
    private List<String> sni;
    private List<String> alpn;
    private TlsMutualConfig mutual;
    private List<String> signers;
    private boolean trustcacerts;

    TlsOptionsConfigBuilder(
        Function<TlsOptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public TlsOptionsConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public TlsOptionsConfigBuilder<T> keys(
        List<String> keys)
    {
        this.keys = keys;
        return this;
    }

    public TlsOptionsConfigBuilder<T> trust(
        List<String> trust)
    {
        this.trust = trust;
        return this;
    }

    public TlsOptionsConfigBuilder<T> sni(
        List<String> sni)
    {
        this.sni = sni;
        return this;
    }

    public TlsOptionsConfigBuilder<T> alpn(
        List<String> alpn)
    {
        this.alpn = alpn;
        return this;
    }

    public TlsOptionsConfigBuilder<T> mutual(
        TlsMutualConfig mutual)
    {
        this.mutual = mutual;
        return this;
    }

    public TlsOptionsConfigBuilder<T> signers(
        List<String> signers)
    {
        this.signers = signers;
        return this;
    }

    public TlsOptionsConfigBuilder<T> trustcacerts(
        boolean trustcacerts)
    {
        this.trustcacerts = trustcacerts;
        return this;
    }

    public T build()
    {
        TlsMutualConfig mutual = this.mutual == null && this.trust != null ? REQUIRED : this.mutual;
        return mapper.apply(new TlsOptionsConfig(version, keys, trust, sni, alpn, mutual, signers, trustcacerts));
    }
}
