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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpPromptArgumentConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpPromptConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpPromptMessageConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpResourceConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpBinding;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpHttpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String HEADERS_NAME = "headers";
    private static final String TOOLS_NAME = "tools";
    private static final String RESOURCES_NAME = "resources";
    private static final String DESCRIPTION_NAME = "description";
    private static final String SUMMARY_NAME = "summary";
    private static final String URI_NAME = "uri";
    private static final String MIME_TYPE_NAME = "mimeType";
    private static final String SCHEMAS_NAME = "schemas";
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";
    private static final String PROMPTS_NAME = "prompts";
    private static final String ARGUMENTS_NAME = "arguments";
    private static final String MESSAGES_NAME = "messages";
    private static final String NAME_NAME = "name";
    private static final String REQUIRED_NAME = "required";
    private static final String ROLE_NAME = "role";
    private static final String TEXT_NAME = "text";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return McpHttpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        McpHttpAuthorizationConfig authorization = mcpHttpOptions.authorization;
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

        List<McpHttpToolConfig> tools = mcpHttpOptions.tools;
        if (tools != null)
        {
            JsonObjectBuilder toolsObject = Json.createObjectBuilder();
            for (McpHttpToolConfig tool : tools)
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
                JsonObjectBuilder schemas = Json.createObjectBuilder();
                if (tool.input != null)
                {
                    schemas.add(INPUT_NAME, model.adaptToJson(tool.input));
                }
                if (tool.output != null)
                {
                    schemas.add(OUTPUT_NAME, model.adaptToJson(tool.output));
                }
                toolObject.add(SCHEMAS_NAME, schemas);
                toolsObject.add(tool.name, toolObject);
            }
            object.add(TOOLS_NAME, toolsObject);
        }

        List<McpHttpResourceConfig> resources = mcpHttpOptions.resources;
        if (resources != null)
        {
            JsonObjectBuilder resourcesObject = Json.createObjectBuilder();
            for (McpHttpResourceConfig resource : resources)
            {
                JsonObjectBuilder resourceObject = Json.createObjectBuilder();
                if (resource.uri != null)
                {
                    resourceObject.add(URI_NAME, resource.uri);
                }
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
                    JsonObjectBuilder schemas = Json.createObjectBuilder();
                    schemas.add(OUTPUT_NAME, model.adaptToJson(resource.output));
                    resourceObject.add(SCHEMAS_NAME, schemas);
                }
                resourcesObject.add(resource.name, resourceObject);
            }
            object.add(RESOURCES_NAME, resourcesObject);
        }

        List<McpHttpPromptConfig> prompts = mcpHttpOptions.prompts;
        if (prompts != null)
        {
            JsonObjectBuilder promptsObject = Json.createObjectBuilder();
            for (McpHttpPromptConfig prompt : prompts)
            {
                JsonObjectBuilder promptObject = Json.createObjectBuilder();
                if (prompt.description != null)
                {
                    promptObject.add(DESCRIPTION_NAME, prompt.description);
                }
                if (prompt.arguments != null)
                {
                    JsonArrayBuilder argumentsArray = Json.createArrayBuilder();
                    for (McpHttpPromptArgumentConfig argument : prompt.arguments)
                    {
                        JsonObjectBuilder argumentObject = Json.createObjectBuilder();
                        argumentObject.add(NAME_NAME, argument.name);
                        if (argument.description != null)
                        {
                            argumentObject.add(DESCRIPTION_NAME, argument.description);
                        }
                        argumentObject.add(REQUIRED_NAME, argument.required);
                        argumentsArray.add(argumentObject);
                    }
                    promptObject.add(ARGUMENTS_NAME, argumentsArray);
                }
                JsonArrayBuilder messagesArray = Json.createArrayBuilder();
                for (McpHttpPromptMessageConfig message : prompt.messages)
                {
                    JsonObjectBuilder messageObject = Json.createObjectBuilder();
                    messageObject.add(ROLE_NAME, message.role);
                    messageObject.add(TEXT_NAME, message.text);
                    messagesArray.add(messageObject);
                }
                promptObject.add(MESSAGES_NAME, messagesArray);
                promptsObject.add(prompt.name, promptObject);
            }
            object.add(PROMPTS_NAME, promptsObject);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        McpHttpAuthorizationConfig authorization = null;
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
                authorization = new McpHttpAuthorizationConfig(name, headers);
            }
        }

        List<McpHttpToolConfig> tools = null;
        if (object.containsKey(TOOLS_NAME))
        {
            JsonObject toolsObject = object.getJsonObject(TOOLS_NAME);
            tools = new ArrayList<>();
            for (String name : toolsObject.keySet())
            {
                JsonObject toolObject = toolsObject.getJsonObject(name);
                String description = toolObject.containsKey(DESCRIPTION_NAME)
                    ? toolObject.getString(DESCRIPTION_NAME)
                    : null;

                String summary = toolObject.containsKey(SUMMARY_NAME)
                    ? toolObject.getString(SUMMARY_NAME)
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

                tools.add(new McpHttpToolConfig(name, description, summary, input, output));
            }
        }

        List<McpHttpResourceConfig> resources = null;
        if (object.containsKey(RESOURCES_NAME))
        {
            JsonObject resourcesObject = object.getJsonObject(RESOURCES_NAME);
            resources = new ArrayList<>();
            for (String name : resourcesObject.keySet())
            {
                JsonObject resourceObject = resourcesObject.getJsonObject(name);
                String uri = resourceObject.containsKey(URI_NAME)
                    ? resourceObject.getString(URI_NAME)
                    : null;

                String description = resourceObject.containsKey(DESCRIPTION_NAME)
                    ? resourceObject.getString(DESCRIPTION_NAME)
                    : null;

                String mimeType = resourceObject.containsKey(MIME_TYPE_NAME)
                    ? resourceObject.getString(MIME_TYPE_NAME)
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

                resources.add(new McpHttpResourceConfig(name, uri, description, mimeType, output));
            }
        }

        List<McpHttpPromptConfig> prompts = null;
        if (object.containsKey(PROMPTS_NAME))
        {
            JsonObject promptsObject = object.getJsonObject(PROMPTS_NAME);
            prompts = new ArrayList<>();
            for (String name : promptsObject.keySet())
            {
                JsonObject promptObject = promptsObject.getJsonObject(name);
                String description = promptObject.containsKey(DESCRIPTION_NAME)
                    ? promptObject.getString(DESCRIPTION_NAME)
                    : null;

                List<McpHttpPromptArgumentConfig> arguments = null;
                if (promptObject.containsKey(ARGUMENTS_NAME))
                {
                    arguments = new ArrayList<>();
                    for (JsonValue value : promptObject.getJsonArray(ARGUMENTS_NAME))
                    {
                        JsonObject argumentObject = value.asJsonObject();
                        String argumentName = argumentObject.getString(NAME_NAME);
                        String argumentDescription = argumentObject.containsKey(DESCRIPTION_NAME)
                            ? argumentObject.getString(DESCRIPTION_NAME)
                            : null;
                        boolean required = argumentObject.containsKey(REQUIRED_NAME) &&
                            argumentObject.getBoolean(REQUIRED_NAME);
                        arguments.add(new McpHttpPromptArgumentConfig(argumentName, argumentDescription, required));
                    }
                }

                List<McpHttpPromptMessageConfig> messages = new ArrayList<>();
                for (JsonValue value : promptObject.getJsonArray(MESSAGES_NAME))
                {
                    JsonObject messageObject = value.asJsonObject();
                    messages.add(new McpHttpPromptMessageConfig(
                        messageObject.getString(ROLE_NAME),
                        messageObject.getString(TEXT_NAME)));
                }

                prompts.add(new McpHttpPromptConfig(name, description, arguments, messages));
            }
        }

        return new McpHttpOptionsConfig(authorization, tools, resources, prompts);
    }
}
