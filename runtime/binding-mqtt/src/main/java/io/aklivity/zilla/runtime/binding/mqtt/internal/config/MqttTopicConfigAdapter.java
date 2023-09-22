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

import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfigAdapter;

public class MqttTopicConfigAdapter implements JsonbAdapter<MqttTopicConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String CONTENT_NAME = "content";

    private final ValidatorConfigAdapter validator = new ValidatorConfigAdapter();

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
            validator.adaptType(topic.content.type);
            JsonValue content = validator.adaptToJson(topic.content);
            object.add(CONTENT_NAME, content);
        }

        return object.build();
    }

    @Override
    public MqttTopicConfig adaptFromJson(
        JsonObject object)
    {
        String name = null;
        if (object.containsKey(NAME_NAME))
        {
            name = object.getString(NAME_NAME);
        }

        ValidatorConfig content = null;
        if (object.containsKey(CONTENT_NAME))
        {
            JsonValue contentJson = object.get(CONTENT_NAME);
            content = validator.adaptFromJson(contentJson);
        }
        return new MqttTopicConfig(name, content);
    }
}
