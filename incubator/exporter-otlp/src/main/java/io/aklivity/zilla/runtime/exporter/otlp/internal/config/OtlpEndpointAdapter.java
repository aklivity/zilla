/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.otlp.internal.config;

import java.net.URI;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOverridesConfig;

public class OtlpEndpointAdapter implements JsonbAdapter<OtlpEndpointConfig, JsonObject>
{
    private static final String PROTOCOL_NAME = "protocol";
    private static final String PROTOCOL_DEFAULT = "http";
    private static final String LOCATION_NAME = "location";
    private static final String OVERRIDES_NAME = "overrides";

    private final OtlpOverridesAdapter overrides;

    public OtlpEndpointAdapter()
    {
        this.overrides = new OtlpOverridesAdapter();
    }

    @Override
    public JsonObject adaptToJson(
        OtlpEndpointConfig endpoint)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (!PROTOCOL_DEFAULT.equals(endpoint.protocol))
        {
            object.add(PROTOCOL_NAME, endpoint.protocol);
        }
        if (endpoint.location != null)
        {
            object.add(LOCATION_NAME, endpoint.location.toString());
        }
        if (endpoint.overrides != null)
        {
            object.add(OVERRIDES_NAME, overrides.adaptToJson(endpoint.overrides));
        }
        return object.build();
    }

    @Override
    public OtlpEndpointConfig adaptFromJson(
        JsonObject object)
    {
        String protocol = object.containsKey(PROTOCOL_NAME)
            ? object.getString(PROTOCOL_NAME)
            : PROTOCOL_DEFAULT;
        URI url = URI.create(object.getString(LOCATION_NAME));
        OtlpOverridesConfig overridesConfig = object.containsKey(OVERRIDES_NAME)
            ? overrides.adaptFromJson(object.getJsonObject(OVERRIDES_NAME))
            : null;
        return new OtlpEndpointConfig(protocol, url, overridesConfig);
    }
}
