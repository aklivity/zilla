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
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaPublishConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaSubscribeConfig;
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

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (!mqttKafkaCondition.subscribes.isEmpty())
        {
            JsonArrayBuilder subscribes = Json.createArrayBuilder();

            mqttKafkaCondition.subscribes.forEach(s ->
            {
                JsonObjectBuilder subscribeJson = Json.createObjectBuilder();
                subscribeJson.add(TOPIC_NAME, s.topic);
                subscribes.add(subscribeJson);
            });
            object.add(SUBSCRIBE_NAME, subscribes);
        }

        if (!mqttKafkaCondition.publishes.isEmpty())
        {
            JsonArrayBuilder publishes = Json.createArrayBuilder();

            mqttKafkaCondition.publishes.forEach(p ->
            {
                JsonObjectBuilder publishJson = Json.createObjectBuilder();
                publishJson.add(TOPIC_NAME, p.topic);
                publishes.add(publishJson);
            });
            object.add(PUBLISH_NAME, publishes);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        List<MqttKafkaSubscribeConfig> subscribes = new ArrayList<>();
        List<MqttKafkaPublishConfig> publishes = new ArrayList<>();

        if (object.containsKey(SUBSCRIBE_NAME))
        {
            JsonArray subscribesJson = object.getJsonArray(SUBSCRIBE_NAME);
            subscribesJson.forEach(s ->
            {
                String topic = s.asJsonObject().getString(TOPIC_NAME);
                subscribes.add(new MqttKafkaSubscribeConfig(topic));
            });
        }

        if (object.containsKey(PUBLISH_NAME))
        {
            JsonArray publishesJson = object.getJsonArray(PUBLISH_NAME);
            publishesJson.forEach(p ->
            {
                String topic = p.asJsonObject().getString(TOPIC_NAME);
                publishes.add(new MqttKafkaPublishConfig(topic));
            });
        }

        return new MqttKafkaConditionConfig(subscribes, publishes);
    }
}
