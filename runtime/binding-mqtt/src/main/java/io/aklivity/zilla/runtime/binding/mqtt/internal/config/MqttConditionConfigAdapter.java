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
package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPublishConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttSessionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttSubscribeConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class MqttConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String SESSION_NAME = "session";
    private static final String SUBSCRIBE_NAME = "subscribe";
    private static final String PUBLISH_NAME = "publish";
    private static final String CLIENT_ID_NAME = "client-id";
    private static final String TOPIC_NAME = "topic";
    public static final String CLIENT_ID_DEFAULT = "*";

    @Override
    public String type()
    {
        return MqttBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        MqttConditionConfig mqttCondition = (MqttConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (!mqttCondition.sessions.isEmpty())
        {
            JsonArrayBuilder sessions = Json.createArrayBuilder();

            mqttCondition.sessions.forEach(p ->
            {
                JsonObjectBuilder sessionJson = Json.createObjectBuilder();
                sessionJson.add(CLIENT_ID_NAME, p.clientId);
                sessions.add(sessionJson);
            });
            object.add(SESSION_NAME, sessions);
        }

        if (!mqttCondition.subscribes.isEmpty())
        {
            JsonArrayBuilder subscribes = Json.createArrayBuilder();

            mqttCondition.subscribes.forEach(s ->
            {
                JsonObjectBuilder subscribeJson = Json.createObjectBuilder();
                subscribeJson.add(TOPIC_NAME, s.topic);
                subscribes.add(subscribeJson);
            });
            object.add(SUBSCRIBE_NAME, subscribes);
        }

        if (!mqttCondition.publishes.isEmpty())
        {
            JsonArrayBuilder publishes = Json.createArrayBuilder();

            mqttCondition.publishes.forEach(p ->
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
        MqttConditionConfigBuilder<MqttConditionConfig> mqttConfig = MqttConditionConfig.builder();

        if (object.containsKey(SESSION_NAME))
        {
            JsonArray sessionsJson = object.getJsonArray(SESSION_NAME);
            sessionsJson.forEach(s ->
            {
                String clientId = s.asJsonObject().getString(CLIENT_ID_NAME, CLIENT_ID_DEFAULT);
                MqttSessionConfig session = new MqttSessionConfig(clientId);
                mqttConfig.session(session);
            });
        }

        if (object.containsKey(SUBSCRIBE_NAME))
        {
            JsonArray subscribesJson = object.getJsonArray(SUBSCRIBE_NAME);
            subscribesJson.forEach(s ->
            {
                String topic = s.asJsonObject().getString(TOPIC_NAME);
                MqttSubscribeConfig subscribe = new MqttSubscribeConfig(topic);
                mqttConfig.subscribe(subscribe);
            });
        }

        if (object.containsKey(PUBLISH_NAME))
        {
            JsonArray publishesJson = object.getJsonArray(PUBLISH_NAME);
            publishesJson.forEach(p ->
            {
                String topic = p.asJsonObject().getString(TOPIC_NAME);
                MqttPublishConfig publish = new MqttPublishConfig(topic);
                mqttConfig.publish(publish);
            });
        }

        return mqttConfig.build();
    }
}
