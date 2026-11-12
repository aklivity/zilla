/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.binding.mqtt.kafka.internal;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionConfig;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionConfigBuilder;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionKind;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaSubscribeConfigBuilder;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public class MqttKafkaConditionConfigAdapter extends ConfigAdapter<ConditionConfig, JsonObject>
{
    private static final String SUBSCRIBE_NAME = "subscribe";
    private static final String PUBLISH_NAME = "publish";
    private static final String TOPIC_NAME = "topic";
    private static final String SHARE_NAME = "share";

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        MqttKafkaConditionConfig mqttKafkaCondition = (MqttKafkaConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mqttKafkaCondition.kind() == MqttKafkaConditionKind.SUBSCRIBE)
        {
            JsonArrayBuilder subscribes = Json.createArrayBuilder();

            mqttKafkaCondition.subscribes.forEach(sub ->
            {
                JsonObjectBuilder subscribeJson = Json.createObjectBuilder();
                subscribeJson.add(TOPIC_NAME, sub.topic);
                if (sub.share != null)
                {
                    subscribeJson.add(SHARE_NAME, sub.share);
                }
                subscribes.add(subscribeJson);
            });
            object.add(SUBSCRIBE_NAME, subscribes);
        }
        else if (mqttKafkaCondition.kind() == MqttKafkaConditionKind.PUBLISH)
        {
            JsonArrayBuilder publishes = Json.createArrayBuilder();

            mqttKafkaCondition.publishes.forEach(topic -> publishes.add(Json.createObjectBuilder().add(TOPIC_NAME, topic)));
            object.add(PUBLISH_NAME, publishes);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        MqttKafkaConditionConfigBuilder<MqttKafkaConditionConfig> builder = MqttKafkaConditionConfig.builder();

        if (object.containsKey(SUBSCRIBE_NAME))
        {
            JsonArray subscribesJson = object.getJsonArray(SUBSCRIBE_NAME);
            subscribesJson.forEach(s ->
            {
                JsonObject subscribeJson = s.asJsonObject();

                MqttKafkaSubscribeConfigBuilder<?> subscribe = builder
                    .subscribe()
                    .topic(subscribeJson.getString(TOPIC_NAME));

                if (subscribeJson.containsKey(SHARE_NAME))
                {
                    subscribe.share(subscribeJson.getString(SHARE_NAME));
                }

                subscribe.build();
            });
        }
        else if (object.containsKey(PUBLISH_NAME))
        {
            JsonArray publishesJson = object.getJsonArray(PUBLISH_NAME);
            publishesJson.forEach(p -> builder.publish(p.asJsonObject().getString(TOPIC_NAME)));
        }

        return builder.build();
    }
}
