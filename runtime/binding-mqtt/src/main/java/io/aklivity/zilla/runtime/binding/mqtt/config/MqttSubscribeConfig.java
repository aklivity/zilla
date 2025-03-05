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

import static java.util.function.Function.identity;

import java.util.List;
import java.util.function.Function;

public class MqttSubscribeConfig
{
    public final String topic;

    public final List<MqttTopicParamConfig> params;

    public static MqttSubscribeConfigBuilder<MqttSubscribeConfig> builder()
    {
        return new MqttSubscribeConfigBuilder<>(identity());
    }

    public static <T> MqttSubscribeConfigBuilder<T> builder(
        Function<MqttSubscribeConfig, T> mapper)
    {
        return new MqttSubscribeConfigBuilder<>(mapper);
    }

    MqttSubscribeConfig(
        String topic,
        List<MqttTopicParamConfig> params)
    {
        this.topic = topic;
        this.params = params;
    }
}
