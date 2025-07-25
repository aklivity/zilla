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

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class JwtOptionsConfigBuilder<T> extends ConfigBuilder<T, JwtOptionsConfigBuilder<T>>
{
    public static final String ROLES_DEFAULT = "scope";

    private final Function<OptionsConfig, T> mapper;

    private String issuer;
    private String audience;
    private String roles;
    private List<JwtKeyConfig> keys;
    private Duration challenge;
    private String identity;
    private String keysURL;

    JwtOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<JwtOptionsConfigBuilder<T>> thisType()
    {
        return (Class<JwtOptionsConfigBuilder<T>>) getClass();
    }

    public JwtOptionsConfigBuilder<T> issuer(
        String issuer)
    {
        this.issuer = issuer;
        return this;
    }

    public JwtOptionsConfigBuilder<T> audience(
        String audience)
    {
        this.audience = audience;
        return this;
    }

    public JwtOptionsConfigBuilder<T> roles(
        String roles)
    {
        this.roles = roles;
        return this;
    }

    public JwtOptionsConfigBuilder<T> challenge(
        Duration challenge)
    {
        this.challenge = challenge;
        return this;
    }

    public JwtOptionsConfigBuilder<T> identity(
        String identity)
    {
        this.identity = identity;
        return this;
    }

    public JwtOptionsConfigBuilder<T> keys(
        List<JwtKeyConfig> keys)
    {
        this.keys = keys;
        return this;
    }

    public JwtKeyConfigBuilder<JwtOptionsConfigBuilder<T>> key()
    {
        return new JwtKeyConfigBuilder<>(this::key);
    }

    public JwtOptionsConfigBuilder<T> key(
        JwtKeyConfig key)
    {
        if (keys == null)
        {
            keys = new LinkedList<>();
        }
        keys.add(key);
        return this;
    }

    public JwtOptionsConfigBuilder<T> keysURL(
        String keysURL)
    {
        this.keysURL = keysURL;
        return this;
    }

    @Override
    public T build()
    {
        String roles = this.roles != null ? this.roles : ROLES_DEFAULT;

        return mapper.apply(new JwtOptionsConfig(issuer, audience, roles, keys, challenge, identity, keysURL));
    }
}
