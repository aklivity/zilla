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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class MqttKafkaConditionConfigBuilder<T> extends ConfigBuilder<T, MqttKafkaConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;
    private final List<MqttKafkaSubscribeConfig> subscribes;
    private final List<String> publishes;

    MqttKafkaConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
        this.subscribes = new ArrayList<>();
        this.publishes = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttKafkaConditionConfigBuilder<T>> thisType()
    {
        return (Class<MqttKafkaConditionConfigBuilder<T>>) getClass();
    }

    public MqttKafkaConditionConfigBuilder<T> subscribe(
        MqttKafkaSubscribeConfig subscribe)
    {
        this.subscribes.add(subscribe);
        return this;
    }

    public MqttKafkaSubscribeConfigBuilder<MqttKafkaConditionConfigBuilder<T>> subscribe()
    {
        return new MqttKafkaSubscribeConfigBuilder<>(this::subscribe);
    }

    public MqttKafkaConditionConfigBuilder<T> publish(
        String topic)
    {
        this.publishes.add(topic);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttKafkaConditionConfig(subscribes, publishes));
    }
}
