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
package io.aklivity.zilla.config.binding.mqtt.kafka;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public class MqttKafkaTopicsConfigBuilder<T> extends ConfigBuilder<T, MqttKafkaTopicsConfigBuilder<T>>
{
    private final Function<MqttKafkaTopicsConfig, T> mapper;

    private String sessions;
    private String messages;
    private String retained;

    MqttKafkaTopicsConfigBuilder(
        Function<MqttKafkaTopicsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttKafkaTopicsConfigBuilder<T>> thisType()
    {
        return (Class<MqttKafkaTopicsConfigBuilder<T>>) getClass();
    }


    public MqttKafkaTopicsConfigBuilder<T> sessions(
        String sessions)
    {
        this.sessions = sessions;
        return this;
    }

    public MqttKafkaTopicsConfigBuilder<T> messages(
        String messages)
    {
        this.messages = messages;
        return this;
    }

    public MqttKafkaTopicsConfigBuilder<T> retained(
        String retained)
    {
        this.retained = retained;
        return this;
    }


    @Override
    public T build()
    {
        return mapper.apply(new MqttKafkaTopicsConfig(sessions, messages, retained));
    }
}
