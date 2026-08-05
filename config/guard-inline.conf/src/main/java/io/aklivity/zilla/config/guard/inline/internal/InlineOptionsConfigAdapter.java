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
package io.aklivity.zilla.config.guard.inline.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.guard.inline.InlineOptionsConfig;

public final class InlineOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String IDENTITY_NAME = "identity";
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String FORMAT_NAME = "format";

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

        if (inlineOptions.format != null)
        {
            object.add(FORMAT_NAME, inlineOptions.format);
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

        String format = object.containsKey(FORMAT_NAME)
            ? object.getString(FORMAT_NAME)
            : null;

        return InlineOptionsConfig.builder()
            .identity(identity)
            .credentials(credentials)
            .format(format)
            .build();
    }
}
