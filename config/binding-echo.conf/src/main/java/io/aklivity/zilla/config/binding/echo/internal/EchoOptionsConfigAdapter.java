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
package io.aklivity.zilla.config.binding.echo.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.echo.EchoOptionsConfig;
import io.aklivity.zilla.config.binding.echo.EchoOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class EchoOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String VALUE_NAME = "value";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        EchoOptionsConfig echoOptions = (EchoOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (echoOptions.value != null)
        {
            model.adaptType(echoOptions.value.model);
            object.add(VALUE_NAME, model.adaptToJson(echoOptions.value));
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        EchoOptionsConfigBuilder<EchoOptionsConfig> echoOptions = EchoOptionsConfig.builder();

        if (object.containsKey(VALUE_NAME))
        {
            JsonValue valueJson = object.get(VALUE_NAME);
            echoOptions.value(model.adaptFromJson(valueJson));
        }

        return echoOptions.build();
    }
}
