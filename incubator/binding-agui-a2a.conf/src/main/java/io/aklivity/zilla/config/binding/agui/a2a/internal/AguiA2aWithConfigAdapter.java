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

package io.aklivity.zilla.config.binding.agui.a2a.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aWithConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.WithConfig;

public final class AguiA2aWithConfigAdapter extends ConfigAdapter<WithConfig, JsonObject>
{
    private static final String AGENT_NAME = "agent";
    private static final String IDENTITY_NAME = "identity";

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        AguiA2aWithConfig aguiA2aWith = (AguiA2aWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (aguiA2aWith.agent != null)
        {
            object.add(AGENT_NAME, aguiA2aWith.agent);
        }

        if (aguiA2aWith.identity != null)
        {
            object.add(IDENTITY_NAME, aguiA2aWith.identity);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String agent = object.containsKey(AGENT_NAME)
            ? object.getString(AGENT_NAME)
            : null;

        String identity = object.containsKey(IDENTITY_NAME)
            ? object.getString(IDENTITY_NAME)
            : null;

        return AguiA2aWithConfig.builder()
            .agent(agent)
            .identity(identity)
            .build();
    }
}
