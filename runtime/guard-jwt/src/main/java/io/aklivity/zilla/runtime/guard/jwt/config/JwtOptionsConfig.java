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
package io.aklivity.zilla.runtime.guard.jwt.config;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class JwtOptionsConfig extends OptionsConfig
{
    public final String issuer;
    public final String audience;
    public final String roles;
    public final List<JwtKeyConfig> keys;
    public final Optional<Duration> challenge;
    public final String identity;
    public final Optional<String> keysURL;

    public static JwtOptionsConfigBuilder<JwtOptionsConfig> builder()
    {
        return new JwtOptionsConfigBuilder<>(JwtOptionsConfig.class::cast);
    }

    public static <T> JwtOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new JwtOptionsConfigBuilder<>(mapper);
    }

    JwtOptionsConfig(
        String issuer,
        String audience,
        String roles,
        List<JwtKeyConfig> keys,
        Duration challenge,
        String identity,
        String keysURL)
    {
        this.issuer = issuer;
        this.audience = audience;
        this.roles = roles;
        this.keys = keys;
        this.challenge = ofNullable(challenge);
        this.identity = identity;
        this.keysURL = ofNullable(keysURL);
    }
}
