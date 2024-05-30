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

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttQoS;

public class MqttKafkaPublishConfig
{
    public final MqttQoS qosMax;

    public static MqttKafkaPublishConfigBuilder<MqttKafkaPublishConfig> builder()
    {
        return new MqttKafkaPublishConfigBuilder<>(MqttKafkaPublishConfig.class::cast);
    }

    public static <T> MqttKafkaPublishConfigBuilder<T> builder(
        Function<MqttKafkaPublishConfig, T> mapper)
    {
        return new MqttKafkaPublishConfigBuilder<>(mapper);
    }

    MqttKafkaPublishConfig(
        MqttQoS qosMax)
    {
        this.qosMax = qosMax;
    }
}

