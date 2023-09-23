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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class MqttCredentialsConfigBuilder<T> extends ConfigBuilder<T, MqttCredentialsConfigBuilder<T>>
{
    private final Function<MqttCredentialsConfig, T> mapper;

    private List<MqttPatternConfig> connects;

    MqttCredentialsConfigBuilder(
        Function<MqttCredentialsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttCredentialsConfigBuilder<T>> thisType()
    {
        return (Class<MqttCredentialsConfigBuilder<T>>) getClass();
    }

    public MqttPatternConfigBuilder<MqttCredentialsConfigBuilder<T>> connect()
    {
        return new MqttPatternConfigBuilder<>(this::connect);
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttCredentialsConfig(connects));
    }

    private MqttCredentialsConfigBuilder<T> connect(
        MqttPatternConfig connect)
    {
        if (connects == null)
        {
            connects = new LinkedList<>();
        }
        connects.add(connect);
        return this;
    }
}
