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

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public final class TlsConditionConfig extends ConditionConfig
{
    public final String authority;
    public final String alpn;

    public static TlsConditionConfigBuilder<TlsConditionConfig> builder()
    {
        return new TlsConditionConfigBuilder<>(TlsConditionConfig.class::cast);
    }

    public static <T> TlsConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new TlsConditionConfigBuilder<>(mapper);
    }

    TlsConditionConfig(
        String authority,
        String alpn)
    {
        this.authority = authority;
        this.alpn = alpn;
    }
}
