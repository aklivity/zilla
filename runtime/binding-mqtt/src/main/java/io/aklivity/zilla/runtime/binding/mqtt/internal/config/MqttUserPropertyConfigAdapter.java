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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttUserPropertyConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttUserPropertyConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;

public class MqttUserPropertyConfigAdapter implements JsonbAdapter<MqttUserPropertyConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String CONTENT_NAME = "content";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        MqttUserPropertyConfig userProperty)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (userProperty.name != null)
        {
            object.add(NAME_NAME, userProperty.name);
        }

        if (userProperty.content != null)
        {
            model.adaptType(userProperty.content.model);
            JsonValue content = model.adaptToJson(userProperty.content);
            object.add(CONTENT_NAME, content);
        }

        return object.build();
    }

    @Override
    public MqttUserPropertyConfig adaptFromJson(
        JsonObject object)
    {
        MqttUserPropertyConfigBuilder<MqttUserPropertyConfig> mqttUserProperty = MqttUserPropertyConfig.builder();
        if (object.containsKey(NAME_NAME))
        {
            mqttUserProperty.name(object.getString(NAME_NAME));
        }

        if (object.containsKey(CONTENT_NAME))
        {
            JsonValue contentJson = object.get(CONTENT_NAME);
            mqttUserProperty.content(model.adaptFromJson(contentJson));
        }
        return mqttUserProperty.build();
    }
}
