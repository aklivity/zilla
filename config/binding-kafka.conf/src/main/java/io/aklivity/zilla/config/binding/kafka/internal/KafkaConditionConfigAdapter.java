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
package io.aklivity.zilla.config.binding.kafka.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.kafka.KafkaConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;

public final class KafkaConditionConfigAdapter implements JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOPIC_NAME = "topic";

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        KafkaConditionConfig kafkaCondition = (KafkaConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (kafkaCondition.topic != null)
        {
            object.add(TOPIC_NAME, kafkaCondition.topic);
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

        return KafkaConditionConfig.builder()
            .topic(topic)
            .build();
    }
}
