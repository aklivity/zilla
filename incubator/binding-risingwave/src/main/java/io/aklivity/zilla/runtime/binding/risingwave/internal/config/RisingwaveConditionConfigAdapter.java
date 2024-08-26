/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;


import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveConditionConfig;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveConditionConfigBuilder;
import io.aklivity.zilla.runtime.binding.risingwave.internal.RisingwaveBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class RisingwaveConditionConfigAdapter implements ConditionConfigAdapterSpi,
    JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String COMMANDS_NAME = "commands";

    @Override
    public String type()
    {
        return RisingwaveBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig adaptable)
    {
        RisingwaveConditionConfig condition = (RisingwaveConditionConfig) adaptable;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (condition.commands != null &&
            !condition.commands.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            condition.commands.forEach(entries::add);

            object.add(COMMANDS_NAME, entries);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        RisingwaveConditionConfigBuilder<RisingwaveConditionConfig> risingwaveCondition =
            RisingwaveConditionConfig.builder();

        if (object.containsKey(COMMANDS_NAME))
        {
            object.getJsonArray(COMMANDS_NAME)
                .forEach(c -> risingwaveCondition.command(c.toString()));
        }

        return risingwaveCondition.build();
    }
}
