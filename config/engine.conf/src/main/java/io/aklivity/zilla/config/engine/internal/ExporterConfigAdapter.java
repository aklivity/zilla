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
package io.aklivity.zilla.config.engine.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.ExporterConfig;
import io.aklivity.zilla.config.engine.ExporterInfo;
import io.aklivity.zilla.config.engine.GenericExporterConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class ExporterConfigAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String VAULT_NAME = "vault";
    private static final String OPTIONS_NAME = "options";

    private final String type;
    private final JsonbAdapter<OptionsConfig, JsonObject> options;

    public ExporterConfigAdapter(
        ExporterInfo info)
    {
        this.type = info.type();
        this.options = info.options();
    }

    public JsonObject adaptToJson(
        ExporterConfig exporter) throws Exception
    {
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

        return item.build();
    }

    public ExporterConfig adaptFromJson(
        String namespace,
        String name,
        JsonObject object) throws Exception
    {
        String vault = null;
        if (object.containsKey(VAULT_NAME))
        {
            vault = object.getString(VAULT_NAME);
        }

        return GenericExporterConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type)
            .vault(vault)
            .options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)))
            .build();
    }
}
