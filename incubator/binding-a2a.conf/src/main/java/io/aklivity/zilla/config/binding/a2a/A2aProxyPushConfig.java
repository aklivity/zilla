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

import static java.util.function.Function.identity;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class A2aProxyPushConfig extends Config
{
    public final String vault;
    public final String key;
    public final Duration ttl;
    public final int maxLength;
    public final List<String> allow;
    public final String exit;
    public final A2aAuthorizationConfig authorization;

    public static A2aProxyPushConfigBuilder<A2aProxyPushConfig> builder()
    {
        return new A2aProxyPushConfigBuilder<>(identity());
    }

    public static <T> A2aProxyPushConfigBuilder<T> builder(
        Function<A2aProxyPushConfig, T> mapper)
    {
        return new A2aProxyPushConfigBuilder<>(mapper);
    }

    A2aProxyPushConfig(
        String vault,
        String key,
        Duration ttl,
        int maxLength,
        List<String> allow,
        String exit,
        A2aAuthorizationConfig authorization)
    {
        this.vault = vault;
        this.key = key;
        this.ttl = ttl;
        this.maxLength = maxLength;
        this.allow = allow;
        this.exit = exit;
        this.authorization = authorization;
    }
}
