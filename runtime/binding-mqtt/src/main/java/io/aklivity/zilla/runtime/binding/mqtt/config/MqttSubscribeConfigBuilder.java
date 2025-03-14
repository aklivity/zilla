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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class MqttSubscribeConfigBuilder<T> extends ConfigBuilder<T, MqttSubscribeConfigBuilder<T>>
{
    private final Function<MqttSubscribeConfig, T> mapper;

    private String topic;

    private List<MqttTopicParamConfig> params;

    MqttSubscribeConfigBuilder(
        Function<MqttSubscribeConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttSubscribeConfigBuilder<T>> thisType()
    {
        return (Class<MqttSubscribeConfigBuilder<T>>) getClass();
    }

    public MqttSubscribeConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    public MqttSubscribeConfigBuilder<T> param(
        MqttTopicParamConfig param)
    {
        if (this.params == null)
        {
            this.params = new ArrayList<>();
        }
        this.params.add(param);
        return this;
    }

    public MqttTopicParamConfigBuilder<MqttSubscribeConfigBuilder<T>> param()
    {
        return new MqttTopicParamConfigBuilder<>(this::param);
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttSubscribeConfig(topic, params));
    }
}
