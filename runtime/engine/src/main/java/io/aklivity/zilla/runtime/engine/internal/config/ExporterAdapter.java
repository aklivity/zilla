/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class ExporterAdapter implements JsonbAdapter<ExporterConfig[], JsonObject>
{
    private static final String TYPE_NAME = "type";
    private static final String VAULT_NAME = "vault";
    private static final String OPTIONS_NAME = "options";

    private final OptionsConfigAdapter options;

    private String namespace;

    public ExporterAdapter(
        ConfigAdapterContext context)
    {
        this.options = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.EXPORTER, context);
    }

    public void adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
    }

    @Override
    public JsonObject adaptToJson(
        ExporterConfig[] exporters)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        for (ExporterConfig exporter: exporters)
        {
            options.adaptType(exporter.type);

            JsonObjectBuilder item = Json.createObjectBuilder();
            item.add(TYPE_NAME, exporter.type);
            if (exporter.vault != null)
            {
                item.add(VAULT_NAME, exporter.vault);
            }
            if (exporter.options != null)
            {
                item.add(OPTIONS_NAME, options.adaptToJson(exporter.options));
            }

            assert namespace.equals(exporter.namespace);
            object.add(exporter.name, item);
        }
        return object.build();
    }

    @Override
    public ExporterConfig[] adaptFromJson(
        JsonObject jsonObject)
    {
        List<ExporterConfig> exporters = new LinkedList<>();
        for (String name : jsonObject.keySet())
        {
            JsonObject item = jsonObject.getJsonObject(name);

            String type = item.getString(TYPE_NAME);
            options.adaptType(type);
            String vault = null;
            if (item.containsKey(VAULT_NAME))
            {
                vault = item.getString(VAULT_NAME);
            }

            exporters.add(ExporterConfig.builder()
                .namespace(namespace)
                .name(name)
                .type(type)
                .vault(vault)
                .options(options.adaptFromJson(item.getJsonObject(OPTIONS_NAME)))
                .build());
        }
        return exporters.toArray(ExporterConfig[]::new);
    }
}
