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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenApiOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenApiToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenApiBinding;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpOpenApiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String TOOLS_NAME = "tools";
    private static final String DESCRIPTION_NAME = "description";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return McpOpenApiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpOpenApiOptionsConfig config = (McpOpenApiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (config.specs != null && !config.specs.isEmpty())
        {
            JsonObjectBuilder specsBuilder = Json.createObjectBuilder();
            for (Map.Entry<String, OpenapiSpecificationConfig> entry : config.specs.entrySet())
            {
                JsonObjectBuilder specBuilder = Json.createObjectBuilder();
                JsonObjectBuilder catalogBuilder = Json.createObjectBuilder();
                for (OpenapiCatalogConfig catalog : entry.getValue().catalogs)
                {
                    JsonObjectBuilder catalogEntryBuilder = Json.createObjectBuilder();
                    catalogEntryBuilder.add(SUBJECT_NAME, catalog.subject);
                    if (catalog.version != null)
                    {
                        catalogEntryBuilder.add(VERSION_NAME, catalog.version);
                    }
                    catalogBuilder.add(catalog.name, catalogEntryBuilder);
                }
                specBuilder.add(CATALOG_NAME, catalogBuilder);
                specsBuilder.add(entry.getKey(), specBuilder);
            }
            object.add(SPECS_NAME, specsBuilder);
        }

        if (config.tools != null && !config.tools.isEmpty())
        {
            JsonObjectBuilder toolsBuilder = Json.createObjectBuilder();
            for (Map.Entry<String, McpOpenApiToolConfig> entry : config.tools.entrySet())
            {
                JsonObjectBuilder toolBuilder = Json.createObjectBuilder();
                if (entry.getValue().description != null)
                {
                    toolBuilder.add(DESCRIPTION_NAME, entry.getValue().description);
                }
                toolsBuilder.add(entry.getKey(), toolBuilder);
            }
            object.add(TOOLS_NAME, toolsBuilder);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        Map<String, OpenapiSpecificationConfig> specs = new LinkedHashMap<>();
        if (object.containsKey(SPECS_NAME))
        {
            JsonObject specsObject = object.getJsonObject(SPECS_NAME);
            for (Map.Entry<String, JsonValue> specEntry : specsObject.entrySet())
            {
                final String apiLabel = specEntry.getKey();
                final JsonObject specObject = specEntry.getValue().asJsonObject();

                List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
                if (specObject.containsKey(CATALOG_NAME))
                {
                    JsonObject catalogObject = specObject.getJsonObject(CATALOG_NAME);
                    for (Map.Entry<String, JsonValue> catalogEntry : catalogObject.entrySet())
                    {
                        OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> catalogBuilder = OpenapiCatalogConfig.builder();
                        JsonObject catalogValue = catalogEntry.getValue().asJsonObject();

                        catalogBuilder.name(catalogEntry.getKey());

                        if (catalogValue.containsKey(SUBJECT_NAME))
                        {
                            catalogBuilder.subject(catalogValue.getString(SUBJECT_NAME));
                        }

                        if (catalogValue.containsKey(VERSION_NAME))
                        {
                            catalogBuilder.version(catalogValue.getString(VERSION_NAME));
                        }

                        catalogs.add(catalogBuilder.build());
                    }
                }
                specs.put(apiLabel, new OpenapiSpecificationConfig(apiLabel, catalogs));
            }
        }

        Map<String, McpOpenApiToolConfig> tools = new LinkedHashMap<>();
        if (object.containsKey(TOOLS_NAME))
        {
            JsonObject toolsObject = object.getJsonObject(TOOLS_NAME);
            for (Map.Entry<String, JsonValue> toolEntry : toolsObject.entrySet())
            {
                JsonObject toolObject = toolEntry.getValue().asJsonObject();
                String description = toolObject.containsKey(DESCRIPTION_NAME)
                    ? toolObject.getString(DESCRIPTION_NAME)
                    : null;
                tools.put(toolEntry.getKey(), new McpOpenApiToolConfig(description));
            }
        }

        return new McpOpenApiOptionsConfig(specs, tools);
    }
}
