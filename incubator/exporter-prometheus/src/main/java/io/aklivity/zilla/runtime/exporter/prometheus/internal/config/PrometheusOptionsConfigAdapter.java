/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.config;

import static io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi.Kind.EXPORTER;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.PrometheusExporter;

public class PrometheusOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String PORT_NAME = "port";
    private static final String PATH_NAME = "path";

    @Override
    public Kind kind()
    {
        return EXPORTER;
    }

    @Override
    public String type()
    {
        return PrometheusExporter.NAME;
    }

    @Override
    public JsonObject adaptToJson(OptionsConfig options)
    {
        PrometheusOptionsConfig prometheusOptionsConfig = (PrometheusOptionsConfig) options;
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(PORT_NAME, prometheusOptionsConfig.port);
        if (prometheusOptionsConfig.path != null)
        {
            object.add(PATH_NAME, prometheusOptionsConfig.path);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(JsonObject object)
    {
        int port = object.containsKey(PORT_NAME)
            ? object.getInt(PORT_NAME)
            : 0;
        String path = object.containsKey(PATH_NAME)
            ? object.getString(PATH_NAME)
            : null;
        return new PrometheusOptionsConfig(port, path);
    }
}
