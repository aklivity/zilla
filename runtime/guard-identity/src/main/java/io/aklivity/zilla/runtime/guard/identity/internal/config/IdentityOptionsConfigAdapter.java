/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.identity.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.guard.identity.internal.IdentityGuard;

public final class IdentityOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String IDENTITY_NAME = "identity";
    private static final String CREDENTIALS_NAME = "credentials";

    @Override
    public String type()
    {
        return IdentityGuard.NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.GUARD;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        IdentityOptionsConfig identityOptions = (IdentityOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (identityOptions.identity != null)
        {
            object.add(IDENTITY_NAME, identityOptions.identity);
        }

        if (identityOptions.credentials != null)
        {
            object.add(CREDENTIALS_NAME, identityOptions.credentials);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        String identity = object.containsKey(IDENTITY_NAME)
            ? object.getString(IDENTITY_NAME)
            : null;

        String credentials = object.containsKey(CREDENTIALS_NAME)
            ? object.getString(CREDENTIALS_NAME)
            : null;

        return new IdentityOptionsConfig(identity, credentials);
    }
}
