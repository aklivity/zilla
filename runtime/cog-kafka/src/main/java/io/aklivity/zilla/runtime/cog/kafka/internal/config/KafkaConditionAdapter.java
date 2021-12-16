/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.config;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaCog;
import io.aklivity.zilla.runtime.engine.config.Condition;
import io.aklivity.zilla.runtime.engine.config.ConditionAdapterSpi;

public final class KafkaConditionAdapter implements ConditionAdapterSpi, JsonbAdapter<Condition, JsonObject>
{
    private static final String TOPIC_NAME = "topic";

    @Override
    public String type()
    {
        return KafkaCog.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        Condition condition)
    {
        KafkaCondition kafkaCondition = (KafkaCondition) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (kafkaCondition.topic != null)
        {
            object.add(TOPIC_NAME, kafkaCondition.topic);
        }

        return object.build();
    }

    @Override
    public Condition adaptFromJson(
        JsonObject object)
    {
        String topic = object.containsKey(TOPIC_NAME)
                ? object.getString(TOPIC_NAME)
                : null;

        return new KafkaCondition(topic);
    }
}
