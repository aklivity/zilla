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
package io.aklivity.zilla.config.binding.amqp.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.amqp.AmqpConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;

public final class AmqpConditionConfigAdapter implements JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String ADDRESS_NAME = "address";
    private static final String CAPABILITIES_NAME = "capabilities";

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        AmqpConditionConfig amqpCondition = (AmqpConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (amqpCondition.address != null)
        {
            object.add(ADDRESS_NAME, amqpCondition.address);
        }

        if (amqpCondition.capabilities != null)
        {
            object.add(CAPABILITIES_NAME, amqpCondition.capabilities);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String address = object.containsKey(ADDRESS_NAME)
                ? object.getString(ADDRESS_NAME)
                : null;

        String capabilities = object.containsKey(CAPABILITIES_NAME)
                ? object.getString(CAPABILITIES_NAME)
                : null;

        return new AmqpConditionConfig(address, capabilities);
    }
}
