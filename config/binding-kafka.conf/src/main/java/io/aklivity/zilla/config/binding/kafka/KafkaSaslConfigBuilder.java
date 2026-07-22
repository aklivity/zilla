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
package io.aklivity.zilla.config.binding.kafka;


import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class KafkaSaslConfigBuilder<T> extends ConfigBuilder<T, KafkaSaslConfigBuilder<T>>
{
    private final Function<KafkaSaslConfig, T> mapper;
    private String mechanism;
    private String username;
    private String password;
    private String token;

    KafkaSaslConfigBuilder(
        Function<KafkaSaslConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaSaslConfigBuilder<T>> thisType()
    {
        return (Class<KafkaSaslConfigBuilder<T>>) getClass();
    }

    public KafkaSaslConfigBuilder<T> mechanism(
        String mechanism)
    {
        this.mechanism = mechanism;
        return this;
    }

    public KafkaSaslConfigBuilder<T> username(
        String username)
    {
        this.username = username;
        return this;
    }

    public KafkaSaslConfigBuilder<T> password(
        String password)
    {
        this.password = password;
        return this;
    }

    public KafkaSaslConfigBuilder<T> token(
        String token)
    {
        this.token = token;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaSaslConfig(mechanism, username, password, token));
    }
}
