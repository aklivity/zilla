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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttVersion;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class MqttOptionsConfigBuilder<T> extends ConfigBuilder<T, MqttOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private MqttAuthorizationConfig authorization;
    private List<MqttTopicConfig> topics;
    private List<MqttVersion> versions;

    MqttOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttOptionsConfigBuilder<T>> thisType()
    {
        return (Class<MqttOptionsConfigBuilder<T>>) getClass();
    }


    public MqttOptionsConfigBuilder<T> topics(
        List<MqttTopicConfig> topics)
    {
        if (topics == null)
        {
            topics = new LinkedList<>();
        }
        this.topics = topics;
        return this;
    }

    public MqttOptionsConfigBuilder<T> topic(
        MqttTopicConfig topic)
    {
        if (this.topics == null)
        {
            this.topics = new LinkedList<>();
        }
        this.topics.add(topic);
        return this;
    }

    public MqttOptionsConfigBuilder<T> versions(
        List<MqttVersion> versions)
    {
        if (versions == null)
        {
            versions = new LinkedList<>();
        }
        this.versions = versions;
        return this;
    }

    public MqttOptionsConfigBuilder<T> version(
        MqttVersion version)
    {
        if (this.versions == null)
        {
            this.versions = new LinkedList<>();
        }
        this.versions.add(version);
        return this;
    }

    public MqttTopicConfigBuilder<MqttOptionsConfigBuilder<T>> topic()
    {
        return new MqttTopicConfigBuilder<>(this::topic);
    }

    private MqttOptionsConfigBuilder<T> authorization(
        MqttAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public MqttAuthorizationConfigBuilder<MqttOptionsConfigBuilder<T>> authorization()
    {
        return new MqttAuthorizationConfigBuilder<>(this::authorization);
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttOptionsConfig(authorization, topics, versions));
    }
}
