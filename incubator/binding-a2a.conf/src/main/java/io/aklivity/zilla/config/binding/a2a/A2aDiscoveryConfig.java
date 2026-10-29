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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class A2aDiscoveryConfig extends Config.Extensible
{
    public final String agent;
    public final A2aDiscoverySearchConfig search;

    public static A2aDiscoveryConfigBuilder<A2aDiscoveryConfig> builder()
    {
        return new A2aDiscoveryConfigBuilder<>(identity());
    }

    public static <T> A2aDiscoveryConfigBuilder<T> builder(
        Function<A2aDiscoveryConfig, T> mapper)
    {
        return new A2aDiscoveryConfigBuilder<>(mapper);
    }

    A2aDiscoveryConfig(
        String agent,
        A2aDiscoverySearchConfig search)
    {
        super(null, search != null ? search.refs() : null);
        this.agent = agent;
        this.search = search;
    }
}
