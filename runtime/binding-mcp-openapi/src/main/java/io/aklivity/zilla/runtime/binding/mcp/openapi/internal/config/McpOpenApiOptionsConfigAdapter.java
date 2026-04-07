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

import static java.util.Collections.unmodifiableSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenApiOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenApiSpecConfig;
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
        McpOpenApiOptionsConfig mcpOptions = (McpOpenApiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpOptions.specs != null && mcpOptions.specs.openapi != null)
        {
            JsonObjectBuilder specs = Json.createObjectBuilder();

            for (OpenapiSpecificationConfig spec : mcpOptions.specs.openapi)
            {
                final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
                final JsonObjectBuilder subjectObject = Json.createObjectBuilder();

                for (OpenapiCatalogConfig catalog : spec.catalogs)
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

        if (mcpOptions.tools != null && !mcpOptions.tools.isEmpty())
        {
            JsonObjectBuilder tools = Json.createObjectBuilder();

            for (Map.Entry<String, McpOpenApiToolConfig> entry : mcpOptions.tools.entrySet())
            {
                JsonObjectBuilder toolObject = Json.createObjectBuilder();
                McpOpenApiToolConfig tool = entry.getValue();

                if (tool.description != null)
                {
                    toolObject.add(DESCRIPTION_NAME, tool.description);
                }

                tools.add(entry.getKey(), toolObject);
            }

            object.add(TOOLS_NAME, tools);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        McpOpenApiSpecConfig specs = null;
        Map<String, McpOpenApiToolConfig> tools = null;

        if (object.containsKey(SPECS_NAME))
        {
            JsonObject specsObject = object.getJsonObject(SPECS_NAME);
            Set<OpenapiSpecificationConfig> openapis = new LinkedHashSet<>();

            for (Map.Entry<String, JsonValue> entry : specsObject.entrySet())
            {
                final String apiLabel = entry.getKey();
                final JsonObject specObject = entry.getValue().asJsonObject();

                if (specObject.containsKey(CATALOG_NAME))
                {
                    final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                    List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
                    for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                    {
                        OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> catalogBuilder = OpenapiCatalogConfig.builder();
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

                    openapis.add(new OpenapiSpecificationConfig(apiLabel, catalogs));
                }
            }

            specs = new McpOpenApiSpecConfig(unmodifiableSet(openapis));
        }

        if (object.containsKey(TOOLS_NAME))
        {
            JsonObject toolsObject = object.getJsonObject(TOOLS_NAME);
            tools = new LinkedHashMap<>();

            for (Map.Entry<String, JsonValue> entry : toolsObject.entrySet())
            {
                JsonObject toolObject = entry.getValue().asJsonObject();
                String description = toolObject.containsKey(DESCRIPTION_NAME)
                    ? toolObject.getString(DESCRIPTION_NAME)
                    : null;

                tools.put(entry.getKey(), new McpOpenApiToolConfig(description));
            }
        }

        return new McpOpenApiOptionsConfig(specs, tools);
    }
}
