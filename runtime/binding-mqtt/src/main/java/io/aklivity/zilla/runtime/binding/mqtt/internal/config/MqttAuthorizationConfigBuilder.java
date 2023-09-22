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
package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class MqttAuthorizationConfigBuilder<T> extends ConfigBuilder<T, MqttAuthorizationConfigBuilder<T>>
{
    private final Function<MqttAuthorizationConfig, T> mapper;

    private String name;
    private MqttCredentialsConfig credentials;

    MqttAuthorizationConfigBuilder(
        Function<MqttAuthorizationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttAuthorizationConfigBuilder<T>> thisType()
    {
        return (Class<MqttAuthorizationConfigBuilder<T>>) getClass();
    }

    public MqttAuthorizationConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public MqttCredentialsConfigBuilder<MqttAuthorizationConfigBuilder<T>> credentials()
    {
        return new MqttCredentialsConfigBuilder<>(this::credentials);
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttAuthorizationConfig(name, credentials));
    }

    private MqttAuthorizationConfigBuilder<T> credentials(
        MqttCredentialsConfig credentials)
    {
        this.credentials = credentials;
        return this;
    }
}
