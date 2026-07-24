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

public class ProxyAddressConfig
{
    public final String host;

    public final Integer port;

    public static ProxyAddressConfigBuilder<ProxyAddressConfig> builder()
    {
        return new ProxyAddressConfigBuilder<>(ProxyAddressConfig.class::cast);
    }

    public static <T> ProxyAddressConfigBuilder<T> builder(
        Function<ProxyAddressConfig, T> mapper)
    {
        return new ProxyAddressConfigBuilder<>(mapper);
    }

    ProxyAddressConfig(
        String host,
        Integer port)
    {
        this.host = host;
        this.port = port;
    }
}
