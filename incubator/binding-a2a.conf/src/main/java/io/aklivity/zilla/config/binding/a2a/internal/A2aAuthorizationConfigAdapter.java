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
import jakarta.json.JsonString;

import io.aklivity.zilla.config.binding.a2a.A2aAuthorizationConfig;
import io.aklivity.zilla.config.binding.a2a.A2aAuthorizationConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class A2aAuthorizationConfigAdapter extends ConfigAdapter<A2aAuthorizationConfig, JsonObject>
{
    private static final String CREDENTIALS_NAME = "credentials";

    private final String defaultCredentials;

    public A2aAuthorizationConfigAdapter(
        String defaultCredentials)
    {
        this.defaultCredentials = defaultCredentials;
    }

    @Override
    public JsonObject adaptToJson(
        A2aAuthorizationConfig authorization)
    {
        JsonObjectBuilder guard = Json.createObjectBuilder();

        if (authorization.credentials != null)
        {
            guard.add(CREDENTIALS_NAME, authorization.credentials);
        }

        return Json.createObjectBuilder()
            .add(authorization.name, guard)
            .build();
    }

    @Override
    public A2aAuthorizationConfig adaptFromJson(
        JsonObject object)
    {
        A2aAuthorizationConfigBuilder<A2aAuthorizationConfig> builder = A2aAuthorizationConfig.builder();

        object.forEach((name, value) ->
        {
            JsonObject guard = (JsonObject) value;
            builder.name(name)
                .credentials(guard.containsKey(CREDENTIALS_NAME)
                    ? ((JsonString) guard.get(CREDENTIALS_NAME)).getString()
                    : defaultCredentials);
        });

        return builder.build();
    }
}
