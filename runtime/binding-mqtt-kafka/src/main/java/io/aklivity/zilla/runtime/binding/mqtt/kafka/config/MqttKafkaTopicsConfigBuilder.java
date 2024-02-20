/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

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
        return mapper.apply(
            new MqttKafkaTopicsConfig(new String16FW(sessions), new String16FW(messages), new String16FW(retained)));
    }
}
