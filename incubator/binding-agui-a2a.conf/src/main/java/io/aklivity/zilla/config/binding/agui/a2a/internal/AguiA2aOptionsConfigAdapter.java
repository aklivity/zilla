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

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aOptionsConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class AguiA2aOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String STORE_NAME = "store";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        final AguiA2aOptionsConfig aguiOptions = (AguiA2aOptionsConfig) options;

        final JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(STORE_NAME, aguiOptions.store);

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        return AguiA2aOptionsConfig.builder()
            .store(object.getString(STORE_NAME))
            .build();
    }
}
