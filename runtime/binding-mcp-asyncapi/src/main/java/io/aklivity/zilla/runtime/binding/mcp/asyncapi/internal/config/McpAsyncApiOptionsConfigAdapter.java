/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.config;

import static java.util.Collections.unmodifiableSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.asyncapi.config.McpAsyncApiOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.asyncapi.config.McpAsyncApiSpecConfig;
import io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.McpAsyncApiBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpAsyncApiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return McpAsyncApiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpAsyncApiOptionsConfig mcpOptions = (McpAsyncApiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpOptions.specs != null && !mcpOptions.specs.isEmpty())
        {
            JsonObjectBuilder specs = Json.createObjectBuilder();

            for (McpAsyncApiSpecConfig spec : mcpOptions.specs)
            {
                final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
                final JsonObjectBuilder subjectObject = Json.createObjectBuilder();

                for (AsyncapiCatalogConfig catalog : spec.catalogs)
                {
                    JsonObjectBuilder schemaObject = Json.createObjectBuilder();
                    schemaObject.add(SUBJECT_NAME, catalog.subject);

                    if (catalog.version != null)
                    {
                        schemaObject.add(VERSION_NAME, catalog.version);
                    }

                    subjectObject.add(catalog.name, schemaObject);
                }

                catalogObject.add(CATALOG_NAME, subjectObject);
                specs.add(spec.label, catalogObject);
            }

            object.add(SPECS_NAME, specs);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        Set<McpAsyncApiSpecConfig> specs = null;

        if (object.containsKey(SPECS_NAME))
        {
            JsonObject specsObject = object.getJsonObject(SPECS_NAME);
            specs = new LinkedHashSet<>();

            for (Map.Entry<String, JsonValue> entry : specsObject.entrySet())
            {
                final String apiLabel = entry.getKey();
                final JsonObject specObject = entry.getValue().asJsonObject();

                if (specObject.containsKey(CATALOG_NAME))
                {
                    final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                    List<AsyncapiCatalogConfig> catalogs = new ArrayList<>();
                    for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                    {
                        AsyncapiCatalogConfigBuilder<AsyncapiCatalogConfig> catalogBuilder = AsyncapiCatalogConfig.builder();
                        JsonObject catalogObject = catalogEntry.getValue().asJsonObject();

                        catalogBuilder.name(catalogEntry.getKey());

                        if (catalogObject.containsKey(SUBJECT_NAME))
                        {
                            catalogBuilder.subject(catalogObject.getString(SUBJECT_NAME));
                        }

                        if (catalogObject.containsKey(VERSION_NAME))
                        {
                            catalogBuilder.version(catalogObject.getString(VERSION_NAME));
                        }

                        catalogs.add(catalogBuilder.build());
                    }

                    specs.add(new McpAsyncApiSpecConfig(apiLabel, catalogs));
                }
            }

            specs = unmodifiableSet(specs);
        }

        return new McpAsyncApiOptionsConfig(specs);
    }
}
