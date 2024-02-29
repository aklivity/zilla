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

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class MqttKafkaWithConfigBuilder<T> extends ConfigBuilder<T, MqttKafkaWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;
    private String messages;

    MqttKafkaWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttKafkaWithConfigBuilder<T>> thisType()
    {
        return (Class<MqttKafkaWithConfigBuilder<T>>) getClass();
    }

    public MqttKafkaWithConfigBuilder<T> messages(
        String messages)
    {
        this.messages = messages;
        return this;
    }
    @Override
    public T build()
    {
        return mapper.apply(new MqttKafkaWithConfig(messages));
    }
}
