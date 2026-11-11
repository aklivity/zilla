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

public final class MqttKafkaSubscribeConfigBuilder<T> extends ConfigBuilder<T, MqttKafkaSubscribeConfigBuilder<T>>
{
    private final Function<MqttKafkaSubscribeConfig, T> mapper;

    private String topic;
    private String share;

    MqttKafkaSubscribeConfigBuilder(
        Function<MqttKafkaSubscribeConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttKafkaSubscribeConfigBuilder<T>> thisType()
    {
        return (Class<MqttKafkaSubscribeConfigBuilder<T>>) getClass();
    }

    public MqttKafkaSubscribeConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    public MqttKafkaSubscribeConfigBuilder<T> share(
        String share)
    {
        this.share = share;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttKafkaSubscribeConfig(topic, share));
    }
}
