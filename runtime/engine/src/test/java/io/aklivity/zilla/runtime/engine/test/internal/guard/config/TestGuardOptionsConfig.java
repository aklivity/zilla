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
package io.aklivity.zilla.runtime.engine.test.internal.guard.config;

import static java.util.function.Function.identity;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TestGuardOptionsConfig extends OptionsConfig
{
    public final String credentials;
    public final Duration lifetime;
    public final Duration challenge;
    public final List<String> roles;

    public static TestGuardOptionsConfigBuilder<TestGuardOptionsConfig> builder()
    {
        return new TestGuardOptionsConfigBuilder<>(identity());
    }

    public static <T> TestGuardOptionsConfigBuilder<T> builder(
        Function<TestGuardOptionsConfig, T> mapper)
    {
        return new TestGuardOptionsConfigBuilder<>(mapper);
    }

    TestGuardOptionsConfig(
        String credentials,
        Duration lifetime,
        Duration challenge,
        List<String> roles)
    {
        this.credentials = credentials;
        this.lifetime = Objects.requireNonNull(lifetime);
        this.challenge = Objects.requireNonNull(challenge);
        this.roles = roles;
    }
}
