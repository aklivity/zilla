/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class KafkaSaslCredentialsConfigBuilder<T> extends ConfigBuilder<T, KafkaSaslCredentialsConfigBuilder<T>>
{
    private final Function<KafkaSaslCredentialsConfig, T> mapper;
    private String mechanism;
    private String username;
    private String password;
    private String token;

    KafkaSaslCredentialsConfigBuilder(
        Function<KafkaSaslCredentialsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaSaslCredentialsConfigBuilder<T>> thisType()
    {
        return (Class<KafkaSaslCredentialsConfigBuilder<T>>) getClass();
    }

    public KafkaSaslCredentialsConfigBuilder<T> mechanism(
        String mechanism)
    {
        this.mechanism = mechanism;
        return this;
    }

    public KafkaSaslCredentialsConfigBuilder<T> username(
        String username)
    {
        this.username = username;
        return this;
    }

    public KafkaSaslCredentialsConfigBuilder<T> password(
        String password)
    {
        this.password = password;
        return this;
    }

    public KafkaSaslCredentialsConfigBuilder<T> token(
        String token)
    {
        this.token = token;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaSaslCredentialsConfig(mechanism, username, password, token));
    }
}
