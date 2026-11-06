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
import jakarta.json.JsonString;

import io.aklivity.zilla.config.binding.agui.AguiOptionsConfig;
import io.aklivity.zilla.config.binding.agui.AguiOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class AguiOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String SERVER_NAME = "server";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_DEFAULT = "Bearer {credentials}";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        AguiOptionsConfig aguiOptions = (AguiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (aguiOptions.server != null)
        {
            object.add(SERVER_NAME, aguiOptions.server);
        }

        if (aguiOptions.authorization != null && aguiOptions.authorization.name != null)
        {
            JsonObjectBuilder guard = Json.createObjectBuilder();
            if (aguiOptions.authorization.credentials != null)
            {
                guard.add(AUTHORIZATION_CREDENTIALS_NAME, aguiOptions.authorization.credentials);
            }

            object.add(AUTHORIZATION_NAME, Json.createObjectBuilder().add(aguiOptions.authorization.name, guard));
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        AguiOptionsConfigBuilder<AguiOptionsConfig> builder = AguiOptionsConfig.builder();

        if (object.containsKey(SERVER_NAME))
        {
            builder.server(object.getString(SERVER_NAME));
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            object.getJsonObject(AUTHORIZATION_NAME).forEach((guard, value) ->
            {
                JsonObject guardObject = (JsonObject) value;
                String credentials = guardObject.containsKey(AUTHORIZATION_CREDENTIALS_NAME)
                    ? ((JsonString) guardObject.get(AUTHORIZATION_CREDENTIALS_NAME)).getString()
                    : AUTHORIZATION_CREDENTIALS_DEFAULT;
                builder.authorization()
                    .name(guard)
                    .credentials(credentials)
                    .build();
            });
        }

        return builder.build();
    }
}
