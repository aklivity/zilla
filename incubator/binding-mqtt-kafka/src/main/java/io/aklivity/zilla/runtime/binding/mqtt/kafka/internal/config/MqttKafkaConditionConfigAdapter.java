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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public class MqttKafkaConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOPIC_NAME = "topic";

    @Override
    public String type()
    {
        return MqttKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        MqttKafkaConditionConfig mqttKafkaCondition = (MqttKafkaConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mqttKafkaCondition.topic != null)
        {
            object.add(TOPIC_NAME, mqttKafkaCondition.topic);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String topic = object.containsKey(TOPIC_NAME)
            ? object.getString(TOPIC_NAME)
            : null;

        return new MqttKafkaConditionConfig(topic);
    }
}
