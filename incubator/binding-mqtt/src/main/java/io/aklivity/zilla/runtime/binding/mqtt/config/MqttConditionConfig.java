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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public final class MqttConditionConfig extends ConditionConfig
{
    public final List<MqttSessionConfig> sessions;
    public final List<MqttSubscribeConfig> subscribes;
    public final List<MqttPublishConfig> publishes;

    public static MqttConditionConfigBuilder<MqttConditionConfig> builder()
    {
        return new MqttConditionConfigBuilder<>(MqttConditionConfig.class::cast);
    }

    public static <T> MqttConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new MqttConditionConfigBuilder<>(mapper);
    }

    MqttConditionConfig(
        List<MqttSessionConfig> sessions,
        List<MqttSubscribeConfig> subscribes,
        List<MqttPublishConfig> publishes)
    {
        this.sessions = sessions;
        this.subscribes = subscribes;
        this.publishes = publishes;
    }
}

