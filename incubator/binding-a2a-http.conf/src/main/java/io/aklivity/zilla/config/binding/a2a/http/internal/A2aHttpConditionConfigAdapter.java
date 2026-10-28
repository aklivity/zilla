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
package io.aklivity.zilla.config.binding.a2a.http.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class A2aHttpConditionConfigAdapter extends ConfigAdapter<ConditionConfig, JsonObject>
{
    private static final String SKILL_NAME = "skill";

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        A2aHttpConditionConfig a2aHttpCondition = (A2aHttpConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (a2aHttpCondition.skill != null)
        {
            object.add(SKILL_NAME, a2aHttpCondition.skill);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String skill = object.containsKey(SKILL_NAME)
            ? object.getString(SKILL_NAME)
            : null;

        return A2aHttpConditionConfig.builder()
            .skill(skill)
            .build();
    }
}
