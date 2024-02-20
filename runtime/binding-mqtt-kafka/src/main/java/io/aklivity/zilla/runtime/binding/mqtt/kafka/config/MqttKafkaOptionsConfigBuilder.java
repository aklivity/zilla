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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class MqttKafkaOptionsConfigBuilder<T> extends ConfigBuilder<T, MqttKafkaOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private MqttKafkaTopicsConfig topics;
    private String serverRef;
    private List<String> clients;
    MqttKafkaOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttKafkaOptionsConfigBuilder<T>> thisType()
    {
        return (Class<MqttKafkaOptionsConfigBuilder<T>>) getClass();
    }


    public MqttKafkaOptionsConfigBuilder<T> topics(
        MqttKafkaTopicsConfig topics)
    {
        this.topics = topics;
        return this;
    }

    public MqttKafkaTopicsConfigBuilder<MqttKafkaOptionsConfigBuilder<T>> topics()
    {
        return new MqttKafkaTopicsConfigBuilder<>(this::topics);
    }

    public MqttKafkaOptionsConfigBuilder<T> serverRef(
        String serverRef)
    {
        this.serverRef = serverRef;
        return this;
    }

    public MqttKafkaOptionsConfigBuilder<T> clients(
        List<String> clients)
    {
        this.clients = clients;
        return this;
    }


    @Override
    public T build()
    {
        return mapper.apply(new MqttKafkaOptionsConfig(topics, serverRef, clients));
    }
}
