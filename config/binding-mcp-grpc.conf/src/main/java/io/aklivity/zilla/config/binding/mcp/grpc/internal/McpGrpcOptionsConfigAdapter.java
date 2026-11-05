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
package io.aklivity.zilla.config.binding.mcp.grpc.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcAuthorizationConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcCatalogConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolAnnotationsConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpGrpcOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String SERVER_NAME = "server";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String HEADERS_NAME = "headers";
    private static final String TOOLS_NAME = "tools";
    private static final String TITLE_NAME = "title";
    private static final String DESCRIPTION_NAME = "description";
    private static final String SUMMARY_NAME = "summary";
    private static final String ANNOTATIONS_NAME = "annotations";
    private static final String READ_ONLY_HINT_NAME = "readOnlyHint";
    private static final String DESTRUCTIVE_HINT_NAME = "destructiveHint";
    private static final String IDEMPOTENT_HINT_NAME = "idempotentHint";
    private static final String OPEN_WORLD_HINT_NAME = "openWorldHint";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpGrpcOptionsConfig mcpGrpcOptions = (McpGrpcOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpGrpcOptions.server != null)
        {
            object.add(SERVER_NAME, mcpGrpcOptions.server);
        }

        McpGrpcCatalogConfig catalog = mcpGrpcOptions.catalog;
        if (catalog != null)
        {
            JsonObjectBuilder schema = Json.createObjectBuilder();
            schema.add(SUBJECT_NAME, catalog.subject);
            if (catalog.version != null)
            {
                schema.add(VERSION_NAME, catalog.version);
            }
            object.add(CATALOG_NAME, Json.createObjectBuilder().add(catalog.name, schema));
        }

        McpGrpcAuthorizationConfig authorization = mcpGrpcOptions.authorization;
        if (authorization != null)
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            if (authorization.headers != null)
            {
                authorization.headers.forEach(headers::add);
            }
            JsonObjectBuilder guard = Json.createObjectBuilder()
                .add(CREDENTIALS_NAME, Json.createObjectBuilder().add(HEADERS_NAME, headers));
            object.add(AUTHORIZATION_NAME, Json.createObjectBuilder().add(authorization.name, guard));
        }

        if (mcpGrpcOptions.tools != null)
        {
            JsonObjectBuilder tools = Json.createObjectBuilder();
            for (McpGrpcToolConfig tool : mcpGrpcOptions.tools)
            {
                JsonObjectBuilder toolObject = Json.createObjectBuilder();
                if (tool.title != null)
                {
                    toolObject.add(TITLE_NAME, tool.title);
                }
                if (tool.description != null)
                {
                    toolObject.add(DESCRIPTION_NAME, tool.description);
                }
                if (tool.summary != null)
                {
                    toolObject.add(SUMMARY_NAME, tool.summary);
                }
                if (tool.annotations != null)
                {
                    toolObject.add(ANNOTATIONS_NAME, annotationsObject(tool.annotations));
                }
                tools.add(tool.name, toolObject);
            }
            object.add(TOOLS_NAME, tools);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        McpGrpcOptionsConfigBuilder<McpGrpcOptionsConfig> mcpGrpcOptions = McpGrpcOptionsConfig.builder();

        if (object.containsKey(SERVER_NAME))
        {
            mcpGrpcOptions.server(object.getString(SERVER_NAME));
        }

        if (object.containsKey(CATALOG_NAME))
        {
            JsonObject catalogs = object.getJsonObject(CATALOG_NAME);
            for (String name : catalogs.keySet())
            {
                JsonObject schema = catalogs.getJsonObject(name);
                mcpGrpcOptions.catalog()
                    .name(name)
                    .subject(schema.getString(SUBJECT_NAME))
                    .version(schema.containsKey(VERSION_NAME) ? schema.getString(VERSION_NAME) : null)
                    .build();
            }
        }

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
                mcpGrpcOptions.authorization(new McpGrpcAuthorizationConfig(name, headers));
            }
        }

        if (object.containsKey(TOOLS_NAME))
        {
            JsonObject tools = object.getJsonObject(TOOLS_NAME);
            for (String name : tools.keySet())
            {
                JsonObject toolObject = tools.getJsonObject(name);
                mcpGrpcOptions.tool()
                    .name(name)
                    .title(toolObject.containsKey(TITLE_NAME) ? toolObject.getString(TITLE_NAME) : null)
                    .description(toolObject.containsKey(DESCRIPTION_NAME) ? toolObject.getString(DESCRIPTION_NAME) : null)
                    .summary(toolObject.containsKey(SUMMARY_NAME) ? toolObject.getString(SUMMARY_NAME) : null)
                    .annotations(toolObject.containsKey(ANNOTATIONS_NAME)
                        ? toolAnnotations(toolObject.getJsonObject(ANNOTATIONS_NAME))
                        : null)
                    .build();
            }
        }

        return mcpGrpcOptions.build();
    }

    private static JsonObjectBuilder annotationsObject(
        McpGrpcToolAnnotationsConfig annotations)
    {
        JsonObjectBuilder annotationsObject = Json.createObjectBuilder();
        if (annotations.readOnlyHint != null)
        {
            annotationsObject.add(READ_ONLY_HINT_NAME, annotations.readOnlyHint);
        }
        if (annotations.destructiveHint != null)
        {
            annotationsObject.add(DESTRUCTIVE_HINT_NAME, annotations.destructiveHint);
        }
        if (annotations.idempotentHint != null)
        {
            annotationsObject.add(IDEMPOTENT_HINT_NAME, annotations.idempotentHint);
        }
        if (annotations.openWorldHint != null)
        {
            annotationsObject.add(OPEN_WORLD_HINT_NAME, annotations.openWorldHint);
        }
        return annotationsObject;
    }

    private static McpGrpcToolAnnotationsConfig toolAnnotations(
        JsonObject annotationsObject)
    {
        return McpGrpcToolAnnotationsConfig.builder()
            .readOnlyHint(hint(annotationsObject, READ_ONLY_HINT_NAME))
            .destructiveHint(hint(annotationsObject, DESTRUCTIVE_HINT_NAME))
            .idempotentHint(hint(annotationsObject, IDEMPOTENT_HINT_NAME))
            .openWorldHint(hint(annotationsObject, OPEN_WORLD_HINT_NAME))
            .build();
    }

    private static Boolean hint(
        JsonObject annotationsObject,
        String name)
    {
        return annotationsObject.containsKey(name)
            ? annotationsObject.getBoolean(name)
            : null;
    }
}
