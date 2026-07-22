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
package io.aklivity.zilla.runtime.guard.inline.internal.config;

import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.guard.inline.internal.InlineGuard;

public final class InlineOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String IDENTITY_NAME = "identity";
    private static final String CREDENTIALS_NAME = "credentials";

    @Override
    public String type()
    {
        return InlineGuard.NAME;
    }

    @Override
    public Set<String> aliases()
    {
        return InlineGuard.ALIASES;
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
        InlineOptionsConfig inlineOptions = (InlineOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (inlineOptions.identity != null)
        {
            object.add(IDENTITY_NAME, inlineOptions.identity);
        }

        if (inlineOptions.credentials != null)
        {
            object.add(CREDENTIALS_NAME, inlineOptions.credentials);
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

        return new InlineOptionsConfig(identity, credentials);
    }
}
