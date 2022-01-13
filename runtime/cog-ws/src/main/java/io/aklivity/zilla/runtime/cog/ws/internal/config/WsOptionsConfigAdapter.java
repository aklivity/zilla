/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.cog.ws.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.cog.ws.internal.WsBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class WsOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String DEFAULTS_NAME = "defaults";
    private static final String PROTOCOL_NAME = "protocol";
    private static final String SCHEME_NAME = "scheme";
    private static final String AUTHORITY_NAME = "authority";
    private static final String PATH_NAME = "path";

    @Override
    public String type()
    {
        return WsBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        WsOptionsConfig wsOptions = (WsOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (wsOptions.protocol != null ||
            wsOptions.scheme != null ||
            wsOptions.authority != null ||
            wsOptions.path != null)
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();

            if (wsOptions.protocol != null)
            {
                entries.add(PROTOCOL_NAME, wsOptions.protocol);
            }

            if (wsOptions.scheme != null)
            {
                entries.add(SCHEME_NAME, wsOptions.scheme);
            }

            if (wsOptions.authority != null)
            {
                entries.add(AUTHORITY_NAME, wsOptions.authority);
            }

            if (wsOptions.path != null)
            {
                entries.add(PATH_NAME, wsOptions.path);
            }

            object.add(DEFAULTS_NAME, entries);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        JsonObject defaults = object.containsKey(DEFAULTS_NAME)
                ? object.getJsonObject(DEFAULTS_NAME)
                : null;

        String protocol = null;
        String scheme = null;
        String authority = null;
        String path = null;

        if (defaults != null)
        {
            if (defaults.containsKey(PROTOCOL_NAME))
            {
                protocol = defaults.getString(PROTOCOL_NAME);
            }

            if (defaults.containsKey(SCHEME_NAME))
            {
                scheme = defaults.getString(SCHEME_NAME);
            }

            if (defaults.containsKey(AUTHORITY_NAME))
            {
                authority = defaults.getString(AUTHORITY_NAME);
            }

            if (defaults.containsKey(PATH_NAME))
            {
                path = defaults.getString(PATH_NAME);
            }
        }

        return new WsOptionsConfig(protocol, scheme, authority, path);
    }
}
