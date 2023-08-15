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

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TestGuardOptionsConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<OptionsConfig, T> mapper;

    private String credentials;
    private Duration lifetime;
    private Duration challenge;
    private List<String> roles;

    public static final Duration DEFAULT_CHALLENGE_NEVER = Duration.ofMillis(0L);

    public static final Duration DEFAULT_LIFETIME_FOREVER = Duration.ofMillis(Long.MAX_VALUE);

    TestGuardOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public TestGuardOptionsConfigBuilder<T> credentials(
        String credentials)
    {
        this.credentials = credentials;
        return this;
    }

    public TestGuardOptionsConfigBuilder<T> lifetime(
        Duration lifetime)
    {
        this.lifetime = lifetime;
        return this;
    }

    public TestGuardOptionsConfigBuilder<T> challenge(
        Duration challenge)
    {
        this.challenge = Objects.requireNonNull(challenge);
        return this;
    }

    public TestGuardOptionsConfigBuilder<T> role(
        String role)
    {
        if (roles == null)
        {
            roles = new LinkedList<>();
        }
        roles.add(role);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestGuardOptionsConfig(
            credentials,
            Optional.ofNullable(lifetime).orElse(DEFAULT_LIFETIME_FOREVER),
            Optional.ofNullable(challenge).orElse(DEFAULT_CHALLENGE_NEVER),
            roles));
    }
}
