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

import io.aklivity.zilla.config.binding.agui.AguiWithConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.WithConfig;

public final class AguiWithConfigAdapter extends ConfigAdapter<WithConfig, JsonObject>
{
    private static final String AGENT_NAME = "agent";

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        final AguiWithConfig aguiWith = (AguiWithConfig) with;

        final JsonObjectBuilder object = Json.createObjectBuilder();

        if (aguiWith.agent != null)
        {
            object.add(AGENT_NAME, aguiWith.agent);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        final String agent = object.containsKey(AGENT_NAME)
            ? object.getString(AGENT_NAME)
            : null;

        return AguiWithConfig.builder()
            .agent(agent)
            .build();
    }
}
