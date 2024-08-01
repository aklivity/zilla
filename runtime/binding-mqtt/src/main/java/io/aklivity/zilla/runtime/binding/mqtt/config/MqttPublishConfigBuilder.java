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
package io.aklivity.zilla.runtime.binding.mqtt.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class MqttPublishConfigBuilder<T> extends ConfigBuilder<T, MqttPublishConfigBuilder<T>>
{
    private final Function<MqttPublishConfig, T> mapper;

    private String topic;

    MqttPublishConfigBuilder(
        Function<MqttPublishConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttPublishConfigBuilder<T>> thisType()
    {
        return (Class<MqttPublishConfigBuilder<T>>) getClass();
    }

    public MqttPublishConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttPublishConfig(topic));
    }
}
