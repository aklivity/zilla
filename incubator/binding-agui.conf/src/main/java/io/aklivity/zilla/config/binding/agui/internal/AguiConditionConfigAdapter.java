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
package io.aklivity.zilla.config.binding.agui.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.agui.AguiConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class AguiConditionConfigAdapter extends ConfigAdapter<ConditionConfig, JsonObject>
{
    private static final String PATH_NAME = "path";
    private static final String AGENT_NAME = "agent";

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        AguiConditionConfig aguiCondition = (AguiConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (aguiCondition.path != null)
        {
            object.add(PATH_NAME, aguiCondition.path);
        }

        if (aguiCondition.agent != null)
        {
            object.add(AGENT_NAME, aguiCondition.agent);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        return AguiConditionConfig.builder()
            .path(object.containsKey(PATH_NAME) ? object.getString(PATH_NAME) : null)
            .agent(object.containsKey(AGENT_NAME) ? object.getString(AGENT_NAME) : null)
            .build();
    }
}
