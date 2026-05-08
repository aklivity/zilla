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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolSchemaConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpHttpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String TOOLS_NAME = "tools";
    private static final String DESCRIPTION_NAME = "description";
    private static final String SCHEMAS_NAME = "schemas";
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";

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

        if (mcpHttpOptions.tools != null && !mcpHttpOptions.tools.isEmpty())
        {
            JsonObjectBuilder toolsBuilder = Json.createObjectBuilder();

            for (Map.Entry<String, McpHttpToolConfig> entry : mcpHttpOptions.tools.entrySet())
            {
                McpHttpToolConfig tool = entry.getValue();
                JsonObjectBuilder toolBuilder = Json.createObjectBuilder();

                if (tool.description != null)
                {
                    toolBuilder.add(DESCRIPTION_NAME, tool.description);
                }

                if (tool.schemas != null)
                {
                    JsonObjectBuilder schemasBuilder = Json.createObjectBuilder();

                    if (tool.schemas.input != null)
                    {
                        schemasBuilder.add(INPUT_NAME, tool.schemas.input);
                    }

                    if (tool.schemas.output != null)
                    {
                        schemasBuilder.add(OUTPUT_NAME, tool.schemas.output);
                    }

                    toolBuilder.add(SCHEMAS_NAME, schemasBuilder);
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
        Map<String, McpHttpToolConfig> tools = null;

        if (object.containsKey(TOOLS_NAME))
        {
            JsonObject toolsJson = object.getJsonObject(TOOLS_NAME);
            tools = new LinkedHashMap<>();

            for (Map.Entry<String, JsonValue> entry : toolsJson.entrySet())
            {
                JsonObject toolJson = entry.getValue().asJsonObject();

                String description = toolJson.containsKey(DESCRIPTION_NAME)
                    ? toolJson.getString(DESCRIPTION_NAME)
                    : null;

                McpHttpToolSchemaConfig schemas = null;
                if (toolJson.containsKey(SCHEMAS_NAME))
                {
                    JsonObject schemasJson = toolJson.getJsonObject(SCHEMAS_NAME);

                    JsonObject input = schemasJson.containsKey(INPUT_NAME)
                        ? schemasJson.getJsonObject(INPUT_NAME)
                        : null;

                    JsonObject output = schemasJson.containsKey(OUTPUT_NAME)
                        ? schemasJson.getJsonObject(OUTPUT_NAME)
                        : null;

                    schemas = new McpHttpToolSchemaConfig(input, output);
                }

                tools.put(entry.getKey(), new McpHttpToolConfig(description, schemas));
            }
        }

        return new McpHttpOptionsConfig(tools);
    }
}
