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

import java.util.Arrays;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.exporter.otlp.internal.OtlpExporter;

public class OtlpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String ENDPOINTS_NAME = "endpoints";

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
        if (otlpOptionsConfig.endpoints != null)
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            Arrays.stream(otlpOptionsConfig.endpoints).forEach(v -> entries.add(endpoint.adaptToJson(v)));
            object.add(ENDPOINTS_NAME, entries);
        }
        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        OtlpEndpointConfig[] e = object.containsKey(ENDPOINTS_NAME)
            ? object.getJsonArray(ENDPOINTS_NAME).stream()
                .map(i -> (JsonObject) i)
                .map(endpoint::adaptFromJson)
                .collect(Collectors.toList())
                .toArray(OtlpEndpointConfig[]::new)
            : null;
        return new OtlpOptionsConfig(e);
    }
}
