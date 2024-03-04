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
package io.aklivity.zilla.runtime.binding.kafka.config;


import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class KafkaSaslConfigBuilder<T> extends ConfigBuilder<T, KafkaSaslConfigBuilder<T>>
{
    private final Function<KafkaSaslConfig, T> mapper;
    private String mechanism;
    private String username;
    private String password;

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

    @Override
    public T build()
    {
        return mapper.apply(new KafkaSaslConfig(mechanism, username, password));
    }
}
