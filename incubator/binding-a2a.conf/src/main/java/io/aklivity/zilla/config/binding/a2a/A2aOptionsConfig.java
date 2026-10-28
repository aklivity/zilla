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

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aOptionsConfig extends OptionsConfig
{
    public final String url;
    public final List<String> versions;
    public final A2aServerPushConfig serverPush;
    public final A2aAuthorizationConfig authorization;
    public final String server;
    public final A2aCacheConfig cache;
    public final A2aDiscoveryConfig discovery;
    public final A2aProxyPushConfig proxyPush;

    A2aOptionsConfig(
        String url,
        List<String> versions,
        A2aServerPushConfig serverPush,
        A2aAuthorizationConfig authorization,
        String server,
        A2aCacheConfig cache,
        A2aDiscoveryConfig discovery,
        A2aProxyPushConfig proxyPush)
    {
        super(null, List.of());
        this.url = url;
        this.versions = versions;
        this.serverPush = serverPush;
        this.authorization = authorization;
        this.server = server;
        this.cache = cache;
        this.discovery = discovery;
        this.proxyPush = proxyPush;
    }

    public static A2aOptionsConfigBuilder<A2aOptionsConfig> builder()
    {
        return new A2aOptionsConfigBuilder<>(A2aOptionsConfig.class::cast);
    }

    public static <T> A2aOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new A2aOptionsConfigBuilder<>(mapper);
    }
}
