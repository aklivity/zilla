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

public final class A2aAuthorizationConfig extends Config
{
    public final String name;
    public final String credentials;

    public transient String qname;

    public static A2aAuthorizationConfigBuilder<A2aAuthorizationConfig> builder()
    {
        return new A2aAuthorizationConfigBuilder<>(identity());
    }

    public static <T> A2aAuthorizationConfigBuilder<T> builder(
        Function<A2aAuthorizationConfig, T> mapper)
    {
        return new A2aAuthorizationConfigBuilder<>(mapper);
    }

    A2aAuthorizationConfig(
        String name,
        String credentials)
    {
        this.name = name;
        this.credentials = credentials;
    }
}
