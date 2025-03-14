/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class MqttSessionConfigBuilder<T> extends ConfigBuilder<T, MqttSessionConfigBuilder<T>>
{
    private final Function<MqttSessionConfig, T> mapper;

    private String clientId;

    MqttSessionConfigBuilder(
        Function<MqttSessionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttSessionConfigBuilder<T>> thisType()
    {
        return (Class<MqttSessionConfigBuilder<T>>) getClass();
    }

    public MqttSessionConfigBuilder<T> clientId(
        String clientId)
    {
        this.clientId = clientId;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttSessionConfig(clientId));
    }
}
