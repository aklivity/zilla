/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class AsyncapiOptionsConfig extends OptionsConfig
{
    public final List<AsyncapiConfig> specs;
    public final String host;
    public final int[] ports;
    public final List<String> keys;
    public final List<String> trust;
    public final List<String> sni;
    public final List<String> alpn;
    public final boolean trustcacerts;

    public static AsyncapiOptionsConfigBuilder<AsyncapiOptionsConfig> builder()
    {
        return new AsyncapiOptionsConfigBuilder<>(AsyncapiOptionsConfig.class::cast);
    }

    public static <T> AsyncapiOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new AsyncapiOptionsConfigBuilder<>(mapper);
    }

    public AsyncapiOptionsConfig(
        List<AsyncapiConfig> specs,
        String host,
        int[] ports,
        List<String> keys,
        List<String> trust,
        List<String> sni,
        List<String> alpn,
        boolean trustcacerts)
    {
        this.specs = specs;
        this.host = host;
        this.ports = ports;
        this.keys = keys;
        this.trust = trust;
        this.sni = sni;
        this.alpn = alpn;
        this.trustcacerts = trustcacerts;
    }
}
