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

import static io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi.Kind.EXPORTER;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.exporter.otlp.internal.OtlpExporter;

public class OtlpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String INTERVAL_NAME = "interval";
    private static final long INTERVAL_DEFAULT = 30;
    private static final String ENDPOINT_NAME = "endpoint";
    private static final String LOCATION_NAME = "location";

    private final OtlpEndpointAdapter endpoint;

    public OtlpOptionsConfigAdapter()
    {
        this.endpoint = new OtlpEndpointAdapter();
    }

    @Override
    public Kind kind()
    {
        return EXPORTER;
    }

    @Override
    public String type()
    {
        return OtlpExporter.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OtlpOptionsConfig otlpOptionsConfig = (OtlpOptionsConfig) options;
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (otlpOptionsConfig.interval != 0)
        {
            object.add(INTERVAL_NAME, otlpOptionsConfig.interval);
        }
        if (otlpOptionsConfig.endpoint != null)
        {
            JsonObjectBuilder endpoint = Json.createObjectBuilder();
            if (otlpOptionsConfig.endpoint.location != null)
            {
                endpoint.add(LOCATION_NAME, otlpOptionsConfig.endpoint.location);
            }
            object.add(ENDPOINT_NAME, endpoint);
        }
        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        long interval = object.containsKey(INTERVAL_NAME)
            ? object.getInt(INTERVAL_NAME) : INTERVAL_DEFAULT;
        OtlpEndpointConfig endpointConfig = object.containsKey(ENDPOINT_NAME)
            ? endpoint.adaptFromJson(object.getJsonObject(ENDPOINT_NAME))
            : null;
        return new OtlpOptionsConfig(interval, endpointConfig);
    }
}
