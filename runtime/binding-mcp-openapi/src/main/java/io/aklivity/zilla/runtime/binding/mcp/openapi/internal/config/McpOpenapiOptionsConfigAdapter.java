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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenapiBinding;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpOpenapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String SERVERS_NAME = "servers";
    private static final String SERVER_URL_NAME = "url";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String TOOLS_NAME = "tools";
    private static final String DESCRIPTION_NAME = "description";
    private static final String SCHEMAS_NAME = "schemas";
    private static final String OUTPUT_NAME = "output";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return McpOpenapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpOpenapiOptionsConfig mcpOpenapiOptions = (McpOpenapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpOpenapiOptions.specs != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (McpOpenapiSpecificationConfig spec : mcpOpenapiOptions.specs)
            {
                final JsonObjectBuilder specObject = Json.createObjectBuilder();
                final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
                for (McpOpenapiCatalogConfig catalog : spec.catalogs)
                {
                    JsonObjectBuilder schemaObject = Json.createObjectBuilder();
                    schemaObject.add(SUBJECT_NAME, catalog.subject);
                    if (catalog.version != null)
                    {
                        schemaObject.add(VERSION_NAME, catalog.version);
                    }
                    catalogObject.add(catalog.name, schemaObject);
                }
                specObject.add(CATALOG_NAME, catalogObject);

                if (spec.servers != null && !spec.servers.isEmpty())
                {
                    final JsonArrayBuilder servers = Json.createArrayBuilder();
                    spec.servers.forEach(s ->
                        servers.add(Json.createObjectBuilder().add(SERVER_URL_NAME, s)));
                    specObject.add(SERVERS_NAME, servers);
                }

                specs.add(spec.label, specObject);
            }
            object.add(SPECS_NAME, specs);
        }

        if (mcpOpenapiOptions.tools != null)
        {
            JsonObjectBuilder toolsObject = Json.createObjectBuilder();
            for (McpOpenapiToolConfig tool : mcpOpenapiOptions.tools)
            {
                JsonObjectBuilder toolObject = Json.createObjectBuilder();
                if (tool.description != null)
                {
                    toolObject.add(DESCRIPTION_NAME, tool.description);
                }
                if (tool.output != null)
                {
                    JsonObjectBuilder schemas = Json.createObjectBuilder();
                    schemas.add(OUTPUT_NAME, model.adaptToJson(tool.output));
                    toolObject.add(SCHEMAS_NAME, schemas);
                }
                toolsObject.add(tool.name, toolObject);
            }
            object.add(TOOLS_NAME, toolsObject);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        McpOpenapiOptionsConfigBuilder<McpOpenapiOptionsConfig> mcpOpenapiOptions = McpOpenapiOptionsConfig.builder();

        if (object.containsKey(SPECS_NAME))
        {
            JsonObject specs = object.getJsonObject(SPECS_NAME);
            for (Map.Entry<String, JsonValue> entry : specs.entrySet())
            {
                final String label = entry.getKey();
                final JsonObject specObject = entry.getValue().asJsonObject();

                final List<String> servers = new LinkedList<>();
                final JsonArray serversJson = specObject.containsKey(SERVERS_NAME)
                    ? specObject.getJsonArray(SERVERS_NAME)
                    : null;
                if (serversJson != null)
                {
                    serversJson.forEach(s ->
                    {
                        JsonObject serverObject = s.asJsonObject();
                        if (serverObject.containsKey(SERVER_URL_NAME))
                        {
                            servers.add(serverObject.getString(SERVER_URL_NAME));
                        }
                    });
                }

                final List<McpOpenapiCatalogConfig> catalogs = new ArrayList<>();
                if (specObject.containsKey(CATALOG_NAME))
                {
                    final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);
                    for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                    {
                        JsonObject catalogObject = catalogEntry.getValue().asJsonObject();
                        String subject = catalogObject.containsKey(SUBJECT_NAME)
                            ? catalogObject.getString(SUBJECT_NAME)
                            : null;
                        String version = catalogObject.containsKey(VERSION_NAME)
                            ? catalogObject.getString(VERSION_NAME)
                            : "latest";
                        catalogs.add(new McpOpenapiCatalogConfig(catalogEntry.getKey(), subject, version));
                    }
                }

                mcpOpenapiOptions.spec(new McpOpenapiSpecificationConfig(label, servers, catalogs));
            }
        }

        if (object.containsKey(TOOLS_NAME))
        {
            JsonObject toolsObject = object.getJsonObject(TOOLS_NAME);
            for (String name : toolsObject.keySet())
            {
                JsonObject toolObject = toolsObject.getJsonObject(name);
                String description = toolObject.containsKey(DESCRIPTION_NAME)
                    ? toolObject.getString(DESCRIPTION_NAME)
                    : null;

                ModelConfig output = null;
                if (toolObject.containsKey(SCHEMAS_NAME))
                {
                    JsonObject schemas = toolObject.getJsonObject(SCHEMAS_NAME);
                    if (schemas.containsKey(OUTPUT_NAME))
                    {
                        output = model.adaptFromJson(schemas.get(OUTPUT_NAME));
                    }
                }

                mcpOpenapiOptions.tool(new McpOpenapiToolConfig(name, description, output));
            }
        }

        return mcpOpenapiOptions.build();
    }
}
