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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public class OtlpEndpointAdapter implements JsonbAdapter<OtlpEndpointConfig, JsonObject>
{
    private static final String LOCATION_NAME = "location";

    @Override
    public JsonObject adaptToJson(
        OtlpEndpointConfig endpoint)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (endpoint.location != null)
        {
            object.add(LOCATION_NAME, endpoint.location);
        }
        return object.build();
    }

    @Override
    public OtlpEndpointConfig adaptFromJson(
        JsonObject object)
    {
        String url = object.getString(LOCATION_NAME);
        return new OtlpEndpointConfig(url);
    }
}
