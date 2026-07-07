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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiResourceConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenapiBinding;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpOpenapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String SECURITY_NAME = "security";
    private static final String SERVER_NAME = "server";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String TOOLS_NAME = "tools";
    private static final String RESOURCES_NAME = "resources";
    private static final String DESCRIPTION_NAME = "description";
    private static final String SCHEMAS_NAME = "schemas";
    private static final String INPUT_NAME = "input";
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

                if (spec.server != null)
                {
                    specObject.add(SERVER_NAME, spec.server);
                }

                if (spec.security != null && !spec.security.isEmpty())
                {
                    final JsonObjectBuilder security = Json.createObjectBuilder();
                    spec.security.forEach(security::add);
                    specObject.add(SECURITY_NAME, security);
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
                if (tool.input != null || tool.output != null)
                {
                    JsonObjectBuilder schemas = Json.createObjectBuilder();
                    if (tool.input != null)
                    {
                        model.adaptType(tool.input.model);
                        schemas.add(INPUT_NAME, model.adaptToJson(tool.input));
                    }
                    if (tool.output != null)
                    {
                        model.adaptType(tool.output.model);
                        schemas.add(OUTPUT_NAME, model.adaptToJson(tool.output));
                    }
                    toolObject.add(SCHEMAS_NAME, schemas);
                }
                toolsObject.add(tool.name, toolObject);
            }
            object.add(TOOLS_NAME, toolsObject);
        }

        if (mcpOpenapiOptions.resources != null)
        {
            JsonObjectBuilder resourcesObject = Json.createObjectBuilder();
            for (McpOpenapiResourceConfig resource : mcpOpenapiOptions.resources)
            {
                JsonObjectBuilder resourceObject = Json.createObjectBuilder();
                if (resource.description != null)
                {
                    resourceObject.add(DESCRIPTION_NAME, resource.description);
                }
                if (resource.output != null)
                {
                    model.adaptType(resource.output.model);
                    JsonObjectBuilder schemas = Json.createObjectBuilder();
                    schemas.add(OUTPUT_NAME, model.adaptToJson(resource.output));
                    resourceObject.add(SCHEMAS_NAME, schemas);
                }
                resourcesObject.add(resource.uri, resourceObject);
            }
            object.add(RESOURCES_NAME, resourcesObject);
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

                final String server = specObject.containsKey(SERVER_NAME)
                    ? specObject.getString(SERVER_NAME)
                    : null;

                McpOpenapiSpecificationConfigBuilder<McpOpenapiOptionsConfigBuilder<McpOpenapiOptionsConfig>> spec =
                    mcpOpenapiOptions.spec()
                        .label(label)
                        .server(server);

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
                        spec.catalog()
                            .name(catalogEntry.getKey())
                            .subject(subject)
                            .version(version)
                            .build();
                    }
                }

                if (specObject.containsKey(SECURITY_NAME))
                {
                    Map<String, String> security = new LinkedHashMap<>();
                    final JsonObject securityObject = specObject.getJsonObject(SECURITY_NAME);
                    for (String scheme : securityObject.keySet())
                    {
                        security.put(scheme, securityObject.getString(scheme));
                    }
                    spec.security(security);
                }

                spec.build();
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

                ModelConfig input = null;
                ModelConfig output = null;
                if (toolObject.containsKey(SCHEMAS_NAME))
                {
                    JsonObject schemas = toolObject.getJsonObject(SCHEMAS_NAME);
                    if (schemas.containsKey(INPUT_NAME))
                    {
                        input = model.adaptFromJson(schemas.get(INPUT_NAME));
                    }
                    if (schemas.containsKey(OUTPUT_NAME))
                    {
                        output = model.adaptFromJson(schemas.get(OUTPUT_NAME));
                    }
                }

                mcpOpenapiOptions.tool()
                    .name(name)
                    .description(description)
                    .input(input)
                    .output(output)
                    .build();
            }
        }

        if (object.containsKey(RESOURCES_NAME))
        {
            JsonObject resourcesObject = object.getJsonObject(RESOURCES_NAME);
            for (String uri : resourcesObject.keySet())
            {
                JsonObject resourceObject = resourcesObject.getJsonObject(uri);
                String description = resourceObject.containsKey(DESCRIPTION_NAME)
                    ? resourceObject.getString(DESCRIPTION_NAME)
                    : null;

                ModelConfig output = null;
                if (resourceObject.containsKey(SCHEMAS_NAME))
                {
                    JsonObject schemas = resourceObject.getJsonObject(SCHEMAS_NAME);
                    if (schemas.containsKey(OUTPUT_NAME))
                    {
                        output = model.adaptFromJson(schemas.get(OUTPUT_NAME));
                    }
                }

                mcpOpenapiOptions.resource()
                    .uri(uri)
                    .description(description)
                    .output(output)
                    .build();
            }
        }

        return mcpOpenapiOptions.build();
    }
}
