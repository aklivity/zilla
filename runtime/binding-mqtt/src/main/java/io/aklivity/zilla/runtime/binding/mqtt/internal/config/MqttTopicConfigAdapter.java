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
package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttUserPropertyConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;

public class MqttTopicConfigAdapter implements JsonbAdapter<MqttTopicConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String CONTENT_NAME = "content";
    private static final String USER_PROPERTIES_NAME = "user-properties";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        MqttTopicConfig topic)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (topic.name != null)
        {
            object.add(NAME_NAME, topic.name);
        }

        if (topic.content != null)
        {
            model.adaptType(topic.content.model);
            JsonValue content = model.adaptToJson(topic.content);
            object.add(CONTENT_NAME, content);
        }

        if (topic.userProperties != null)
        {
            JsonObjectBuilder userProperties = Json.createObjectBuilder();
            for (MqttUserPropertyConfig userProperty : topic.userProperties)
            {
                model.adaptType(userProperty.value.model);
                userProperties.add(userProperty.name, model.adaptToJson(userProperty.value));
            }
            object.add(USER_PROPERTIES_NAME, userProperties);
        }

        return object.build();
    }

    @Override
    public MqttTopicConfig adaptFromJson(
        JsonObject object)
    {
        MqttTopicConfigBuilder<MqttTopicConfig> mqttTopic = MqttTopicConfig.builder();
        if (object.containsKey(NAME_NAME))
        {
            mqttTopic.name(object.getString(NAME_NAME));
        }

        if (object.containsKey(CONTENT_NAME))
        {
            JsonValue contentJson = object.get(CONTENT_NAME);
            mqttTopic.content(model.adaptFromJson(contentJson));
        }

        if (object.containsKey(USER_PROPERTIES_NAME))
        {
            JsonObject userPropertiesJson = object.getJsonObject(USER_PROPERTIES_NAME);
            List<MqttUserPropertyConfig> userProperties = new LinkedList<>();
            for (Map.Entry<String, JsonValue> entry : userPropertiesJson.entrySet())
            {
                MqttUserPropertyConfig header = MqttUserPropertyConfig.builder()
                    .name(entry.getKey())
                    .value(model.adaptFromJson(entry.getValue()))
                    .build();
                userProperties.add(header);
            }
            mqttTopic.userProperties(userProperties);
        }

        return mqttTopic.build();
    }
}
