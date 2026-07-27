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
package io.aklivity.zilla.config.binding.mcp.openapi.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiAuthorizationConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiCatalogConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiCatalogConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiResourceConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiSpecificationConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiSpecificationConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiToolConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpOpenapiOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String HEADERS_NAME = "headers";
    private static final String SPECS_NAME = "specs";
    private static final String SECURITY_NAME = "security";
    private static final String OVERLAY_NAME = "overlay";
    private static final String SERVER_NAME = "server";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String TOOLS_NAME = "tools";
    private static final String RESOURCES_NAME = "resources";
    private static final String DESCRIPTION_NAME = "description";
    private static final String SUMMARY_NAME = "summary";
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";
    private static final String MIME_TYPE_NAME = "mimeType";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpOpenapiOptionsConfig mcpOpenapiOptions = (McpOpenapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        McpOpenapiAuthorizationConfig authorization = mcpOpenapiOptions.authorization;
        if (authorization != null)
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            if (authorization.headers != null)
            {
                authorization.headers.forEach(headers::add);
            }
            JsonObjectBuilder credentials = Json.createObjectBuilder();
            credentials.add(HEADERS_NAME, headers);
            JsonObjectBuilder guard = Json.createObjectBuilder();
            guard.add(CREDENTIALS_NAME, credentials);
            JsonObjectBuilder authorizations = Json.createObjectBuilder();
            authorizations.add(authorization.name, guard);
            object.add(AUTHORIZATION_NAME, authorizations);
        }

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

                if (spec.overlay != null)
                {
                    final JsonObjectBuilder overlaySchema = Json.createObjectBuilder();
                    overlaySchema.add(SUBJECT_NAME, spec.overlay.subject);
                    if (spec.overlay.version != null)
                    {
                        overlaySchema.add(VERSION_NAME, spec.overlay.version);
                    }

                    final JsonObjectBuilder overlaySubject = Json.createObjectBuilder();
                    overlaySubject.add(spec.overlay.name, overlaySchema);
                    specObject.add(OVERLAY_NAME, overlaySubject);
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
                if (tool.summary != null)
                {
                    toolObject.add(SUMMARY_NAME, tool.summary);
                }
                if (tool.input != null)
                {
                    model.adaptType(tool.input.model);
                    toolObject.add(INPUT_NAME, model.adaptToJson(tool.input));
                }
                if (tool.output != null)
                {
                    model.adaptType(tool.output.model);
                    toolObject.add(OUTPUT_NAME, model.adaptToJson(tool.output));
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
                if (resource.mimeType != null)
                {
                    resourceObject.add(MIME_TYPE_NAME, resource.mimeType);
                }
                if (resource.output != null)
                {
                    model.adaptType(resource.output.model);
                    resourceObject.add(OUTPUT_NAME, model.adaptToJson(resource.output));
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

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            JsonObject authorizations = object.getJsonObject(AUTHORIZATION_NAME);
            for (String name : authorizations.keySet())
            {
                Map<String, String> headers = new LinkedHashMap<>();
                JsonObject guard = authorizations.getJsonObject(name);
                if (guard.containsKey(CREDENTIALS_NAME))
                {
                    JsonObject credentials = guard.getJsonObject(CREDENTIALS_NAME);
                    if (credentials.containsKey(HEADERS_NAME))
                    {
                        JsonObject headersObject = credentials.getJsonObject(HEADERS_NAME);
                        for (String header : headersObject.keySet())
                        {
                            headers.put(header, headersObject.getString(header));
                        }
                    }
                }
                mcpOpenapiOptions.authorization(new McpOpenapiAuthorizationConfig(name, headers));
            }
        }

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

                if (specObject.containsKey(OVERLAY_NAME))
                {
                    final JsonObject overlayObject = specObject.getJsonObject(OVERLAY_NAME);
                    final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
                    final JsonObject overlaySchemaObject = overlayEntry.getValue().asJsonObject();

                    final McpOpenapiCatalogConfigBuilder<?> overlayBuilder = spec.overlay();
                    overlayBuilder.name(overlayEntry.getKey());

                    if (overlaySchemaObject.containsKey(SUBJECT_NAME))
                    {
                        overlayBuilder.subject(overlaySchemaObject.getString(SUBJECT_NAME));
                    }

                    if (overlaySchemaObject.containsKey(VERSION_NAME))
                    {
                        overlayBuilder.version(overlaySchemaObject.getString(VERSION_NAME));
                    }

                    overlayBuilder.build();
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
                String summary = toolObject.containsKey(SUMMARY_NAME)
                    ? toolObject.getString(SUMMARY_NAME)
                    : null;

                ModelConfig input = toolObject.containsKey(INPUT_NAME)
                    ? model.adaptFromJson(toolObject.get(INPUT_NAME))
                    : null;
                ModelConfig output = toolObject.containsKey(OUTPUT_NAME)
                    ? model.adaptFromJson(toolObject.get(OUTPUT_NAME))
                    : null;

                mcpOpenapiOptions.tool()
                    .name(name)
                    .description(description)
                    .summary(summary)
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

                String mimeType = resourceObject.containsKey(MIME_TYPE_NAME)
                    ? resourceObject.getString(MIME_TYPE_NAME)
                    : null;

                ModelConfig output = resourceObject.containsKey(OUTPUT_NAME)
                    ? model.adaptFromJson(resourceObject.get(OUTPUT_NAME))
                    : null;

                mcpOpenapiOptions.resource()
                    .uri(uri)
                    .description(description)
                    .mimeType(mimeType)
                    .output(output)
                    .build();
            }
        }

        return mcpOpenapiOptions.build();
    }
}
