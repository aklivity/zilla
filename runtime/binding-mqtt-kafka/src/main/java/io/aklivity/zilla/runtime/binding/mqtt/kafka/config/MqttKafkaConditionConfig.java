/*
 * Copyright 2021-2024 Aklivity Inc
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

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public class MqttKafkaConditionConfig extends ConditionConfig
{
    public final List<String> topics;
    public final MqttKafkaConditionKind kind;

    public static MqttKafkaConditionConfigBuilder<MqttKafkaConditionConfig> builder()
    {
        return new MqttKafkaConditionConfigBuilder<>(MqttKafkaConditionConfig.class::cast);
    }

    public static <T> MqttKafkaConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new MqttKafkaConditionConfigBuilder<>(mapper);
    }

    MqttKafkaConditionConfig(
        List<String> topics,
        MqttKafkaConditionKind kind)
    {
        this.topics = topics;
        this.kind = kind;
    }
}
