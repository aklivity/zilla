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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;

public final class KafkaTopicConfigAdapter implements JsonbAdapter<KafkaTopicConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String DEFAULT_OFFSET_NAME = "defaultOffset";
    private static final String DELTA_TYPE_NAME = "deltaType";
    private static final String EVENT_KEY = "key";
    private static final String EVENT_VALUE = "value";
    private static final String SUBJECT = "subject";

    private final ModelConfigAdapter converter = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        KafkaTopicConfig topic)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (topic.name != null)
        {
            object.add(NAME_NAME, topic.name);
        }
        if (topic.defaultOffset != null)
        {
            object.add(DEFAULT_OFFSET_NAME, topic.defaultOffset.toString().toLowerCase());
        }
        if (topic.deltaType != null)
        {
            object.add(DELTA_TYPE_NAME, topic.deltaType.toString().toLowerCase());
        }

        if (topic.key != null)
        {
            converter.adaptType(topic.key.model);

            object.add(EVENT_KEY, converter.adaptToJson(topic.key));
        }

        if (topic.value != null)
        {
            converter.adaptType(topic.value.model);

            object.add(EVENT_VALUE, converter.adaptToJson(topic.value));
        }

        return object.build();
    }

    @Override
    public KafkaTopicConfig adaptFromJson(
        JsonObject object)
    {
        String name = object.containsKey(NAME_NAME)
                ? object.getString(NAME_NAME)
                : null;

        KafkaOffsetType defaultOffset = object.containsKey(DEFAULT_OFFSET_NAME)
                ? KafkaOffsetType.valueOf(object.getString(DEFAULT_OFFSET_NAME).toUpperCase())
                : null;

        KafkaDeltaType deltaType = object.containsKey(DELTA_TYPE_NAME)
                ? KafkaDeltaType.valueOf(object.getString(DELTA_TYPE_NAME).toUpperCase())
                : null;

        JsonObject key = object.containsKey(EVENT_KEY)
                ? object.getJsonObject(EVENT_KEY)
                : null;

        ModelConfig keyConfig = null;

        if (key != null)
        {
            JsonObjectBuilder keyObject = Json.createObjectBuilder();

            key.forEach(keyObject::add);
            keyObject.add(SUBJECT, name + "-key");

            keyConfig = converter.adaptFromJson(keyObject.build());
        }

        JsonObject value = object.containsKey(EVENT_VALUE)
                ? object.getJsonObject(EVENT_VALUE)
                : null;

        ModelConfig valueConfig = null;

        if (value != null)
        {
            JsonObjectBuilder valueObject = Json.createObjectBuilder();

            value.forEach(valueObject::add);
            valueObject.add(SUBJECT, name + "-value");

            valueConfig = converter.adaptFromJson(valueObject.build());
        }

        return new KafkaTopicConfig(name, defaultOffset, deltaType, keyConfig, valueConfig);
    }
}
