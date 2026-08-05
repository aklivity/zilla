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

public class ProxyInfoConfig
{
    public final String alpn;

    public final String authority;

    public final byte[] identity;

    public final String namespace;

    public final ProxySecureInfoConfig secure;

    public static ProxyInfoConfigBuilder<ProxyInfoConfig> builder()
    {
        return new ProxyInfoConfigBuilder<>(ProxyInfoConfig.class::cast);
    }

    public static <T> ProxyInfoConfigBuilder<T> builder(
        Function<ProxyInfoConfig, T> mapper)
    {
        return new ProxyInfoConfigBuilder<>(mapper);
    }

    ProxyInfoConfig(
        String alpn,
        String authority,
        byte[] identity,
        String namespace,
        ProxySecureInfoConfig secure)
    {
        this.alpn = alpn;
        this.authority = authority;
        this.identity = identity;
        this.namespace = namespace;
        this.secure = secure;
    }
}
