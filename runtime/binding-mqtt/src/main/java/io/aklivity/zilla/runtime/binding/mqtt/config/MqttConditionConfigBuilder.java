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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class MqttConditionConfigBuilder<T> extends ConfigBuilder<T, MqttConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private final List<MqttSessionConfig> session;
    private final List<MqttSubscribeConfig> subscribe;
    private final List<MqttPublishConfig> publish;

    MqttConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
        this.session = new ArrayList<>();
        this.subscribe = new ArrayList<>();
        this.publish = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttConditionConfigBuilder<T>> thisType()
    {
        return (Class<MqttConditionConfigBuilder<T>>) getClass();
    }

    public MqttConditionConfigBuilder<T> session(
        MqttSessionConfig session)
    {
        this.session.add(session);
        return this;
    }

    public MqttConditionConfigBuilder<T> subscribe(
        MqttSubscribeConfig subscribe)
    {
        this.subscribe.add(subscribe);
        return this;
    }

    public MqttSubscribeConfigBuilder<MqttConditionConfigBuilder<T>> subscribe()
    {
        return new MqttSubscribeConfigBuilder<>(this::subscribe);
    }

    public MqttConditionConfigBuilder<T> publish(
        MqttPublishConfig publish)
    {
        this.publish.add(publish);
        return this;
    }

    public MqttPublishConfigBuilder<MqttConditionConfigBuilder<T>> publish()
    {
        return new MqttPublishConfigBuilder<>(this::publish);
    }

    @Override
    public T build()
    {
        return mapper.apply(new MqttConditionConfig(session, subscribe, publish));
    }
}
