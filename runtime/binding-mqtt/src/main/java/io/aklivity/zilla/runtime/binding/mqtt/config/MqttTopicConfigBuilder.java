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
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;

public class MqttTopicConfigBuilder<T> extends ConfigBuilder<T, MqttTopicConfigBuilder<T>>
{
    private final Function<MqttTopicConfig, T> mapper;

    private String name;
    private ConverterConfig content;

    MqttTopicConfigBuilder(
        Function<MqttTopicConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttTopicConfigBuilder<T>> thisType()
    {
        return (Class<MqttTopicConfigBuilder<T>>) getClass();
    }

    public MqttTopicConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public MqttTopicConfigBuilder<T> content(
        ConverterConfig content)
    {
        this.content = content;
        return this;
    }

    public <C extends ConfigBuilder<MqttTopicConfigBuilder<T>, C>> C content(
        Function<Function<ConverterConfig, MqttTopicConfigBuilder<T>>, C> content)
    {
        return content.apply(this::content);
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttTopicConfig(name, content));
    }
}
