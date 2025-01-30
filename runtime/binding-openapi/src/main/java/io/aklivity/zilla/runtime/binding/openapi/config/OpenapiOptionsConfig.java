/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class OpenapiOptionsConfig extends OptionsConfig
{
    public final List<String> servers;
    public final TcpOptionsConfig tcp;
    public final TlsOptionsConfig tls;
    public final HttpOptionsConfig http;
    public final List<OpenapiSpecificationConfig> specs;

    public static OpenapiOptionsConfigBuilder<OpenapiOptionsConfig> builder()
    {
        return new OpenapiOptionsConfigBuilder<>(OpenapiOptionsConfig.class::cast);
    }

    public static <T> OpenapiOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new OpenapiOptionsConfigBuilder<>(mapper);
    }

    OpenapiOptionsConfig(
        List<String> servers,
        TcpOptionsConfig tcp,
        TlsOptionsConfig tls,
        HttpOptionsConfig http,
        List<OpenapiSpecificationConfig> specs)
    {
        this.servers = servers;
        this.tcp = tcp;
        this.tls = tls;
        this.http = http;
        this.specs = specs;
    }
}
