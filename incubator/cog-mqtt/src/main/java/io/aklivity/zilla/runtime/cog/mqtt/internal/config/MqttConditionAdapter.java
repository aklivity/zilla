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
package io.aklivity.zilla.runtime.cog.mqtt.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.cog.mqtt.internal.MqttCog;
import io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttCapabilities;
import io.aklivity.zilla.runtime.engine.config.Condition;
import io.aklivity.zilla.runtime.engine.config.ConditionAdapterSpi;

public final class MqttConditionAdapter implements ConditionAdapterSpi, JsonbAdapter<Condition, JsonObject>
{
    private static final String TOPIC_NAME = "topic";
    private static final String CAPABILITIES_NAME = "capabilities";

    @Override
    public String type()
    {
        return MqttCog.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        Condition condition)
    {
        MqttCondition mqttCondition = (MqttCondition) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mqttCondition.topic != null)
        {
            object.add(TOPIC_NAME, mqttCondition.topic);
        }

        if (mqttCondition.capabilities != null)
        {
            object.add(CAPABILITIES_NAME, mqttCondition.capabilities.toString().toLowerCase());
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

        MqttCapabilities capabilities = object.containsKey(CAPABILITIES_NAME)
                ? MqttCapabilities.valueOf(object.getString(CAPABILITIES_NAME).toUpperCase())
                : null;

        return new MqttCondition(topic, capabilities);
    }
}
