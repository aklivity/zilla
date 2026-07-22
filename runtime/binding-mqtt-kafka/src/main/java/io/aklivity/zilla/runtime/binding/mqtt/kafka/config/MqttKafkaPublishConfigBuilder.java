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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.config;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public class MqttKafkaPublishConfigBuilder<T> extends ConfigBuilder<T, MqttKafkaPublishConfigBuilder<T>>
{
    private final Function<MqttKafkaPublishConfig, T> mapper;

    private String qosMax;

    MqttKafkaPublishConfigBuilder(
        Function<MqttKafkaPublishConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttKafkaPublishConfigBuilder<T>> thisType()
    {
        return (Class<MqttKafkaPublishConfigBuilder<T>>) getClass();
    }


    public MqttKafkaPublishConfigBuilder<T> qosMax(
        String qosMax)
    {
        this.qosMax = qosMax;
        return this;
    }


    @Override
    public T build()
    {
        return mapper.apply(new MqttKafkaPublishConfig(qosMax));
    }
}
