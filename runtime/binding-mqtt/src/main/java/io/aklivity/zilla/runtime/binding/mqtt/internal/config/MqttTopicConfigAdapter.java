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

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
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
    private final MqttUserPropertyConfigAdapter mqttUserProperty = new MqttUserPropertyConfigAdapter();

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
            JsonArrayBuilder userProperties = Json.createArrayBuilder();
            topic.userProperties.stream()
                .map(mqttUserProperty::adaptToJson)
                .forEach(userProperties::add);
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
            List<MqttUserPropertyConfig> userProperties = object.getJsonArray(USER_PROPERTIES_NAME).stream()
                .map(item -> mqttUserProperty.adaptFromJson((JsonObject) item))
                .collect(Collectors.toList());
            mqttTopic.userProperties(userProperties);
        }

        return mqttTopic.build();
    }
}
