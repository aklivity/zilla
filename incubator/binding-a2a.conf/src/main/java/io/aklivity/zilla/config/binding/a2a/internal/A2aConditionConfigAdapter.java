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
package io.aklivity.zilla.config.binding.a2a.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.a2a.A2aConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.runtime.common.json.JsonStrings;

public final class A2aConditionConfigAdapter extends ConfigAdapter<ConditionConfig, JsonObject>
{
    private static final String AGENT_NAME = "agent";
    private static final String SKILL_NAME = "skill";

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        A2aConditionConfig a2aCondition = (A2aConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        JsonStrings.addStringOrArray(object, AGENT_NAME, a2aCondition.agent);
        JsonStrings.addStringOrArray(object, SKILL_NAME, a2aCondition.skill);

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        return A2aConditionConfig.builder()
            .agent(JsonStrings.asStringOrArray(object, AGENT_NAME))
            .skill(JsonStrings.asStringOrArray(object, SKILL_NAME))
            .build();
    }
}
