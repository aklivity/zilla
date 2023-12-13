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

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionKind;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public class MqttKafkaConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String SUBSCRIBE_NAME = "subscribe";
    private static final String PUBLISH_NAME = "publish";
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
        JsonArrayBuilder topics = Json.createArrayBuilder();

        mqttKafkaCondition.topics.forEach(s ->
        {
            JsonObjectBuilder subscribeJson = Json.createObjectBuilder();
            subscribeJson.add(TOPIC_NAME, s);
            topics.add(subscribeJson);
        });

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mqttKafkaCondition.kind == MqttKafkaConditionKind.SUBSCRIBE)
        {
            object.add(SUBSCRIBE_NAME, topics);
        }
        else if (mqttKafkaCondition.kind == MqttKafkaConditionKind.PUBLISH)
        {
            object.add(PUBLISH_NAME, topics);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        List<String> topics = new ArrayList<>();
        MqttKafkaConditionKind kind = null;

        if (object.containsKey(SUBSCRIBE_NAME))
        {
            kind = MqttKafkaConditionKind.SUBSCRIBE;
            JsonArray subscribesJson = object.getJsonArray(SUBSCRIBE_NAME);
            subscribesJson.forEach(s ->
            {
                String topic = s.asJsonObject().getString(TOPIC_NAME);
                topics.add(topic);
            });
        }
        else if (object.containsKey(PUBLISH_NAME))
        {
            kind = MqttKafkaConditionKind.PUBLISH;
            JsonArray publishesJson = object.getJsonArray(PUBLISH_NAME);
            publishesJson.forEach(p ->
            {
                String topic = p.asJsonObject().getString(TOPIC_NAME);
                topics.add(topic);
            });
        }

        return new MqttKafkaConditionConfig(topics, kind);
    }
}
