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

import static java.util.function.Function.identity;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TlsOptionsConfig extends OptionsConfig
{
    public final String version;
    public final List<String> keys;
    public final List<String> trust;
    public final List<String> sni;
    public final List<String> alpn;
    public final TlsMutualConfig mutual;
    public final List<String> signers;
    public final boolean trustcacerts;

    public static TlsOptionsConfigBuilder<TlsOptionsConfig> builder()
    {
        return new TlsOptionsConfigBuilder<>(identity());
    }

    public static <T> TlsOptionsConfigBuilder<T> builder(
        Function<TlsOptionsConfig, T> mapper)
    {
        return new TlsOptionsConfigBuilder<>(mapper);
    }

    TlsOptionsConfig(
        String version,
        List<String> keys,
        List<String> trust,
        List<String> sni,
        List<String> alpn,
        TlsMutualConfig mutual,
        List<String> signers,
        boolean trustcacerts)
    {
        this.version = version;
        this.keys = keys;
        this.trust = trust;
        this.sni = sni;
        this.alpn = alpn;
        this.mutual = mutual;
        this.signers = signers;
        this.trustcacerts = trustcacerts;
    }
}
