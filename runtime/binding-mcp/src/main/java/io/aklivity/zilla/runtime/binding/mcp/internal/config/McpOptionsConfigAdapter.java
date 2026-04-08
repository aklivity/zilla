/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.config.McpPromptConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String PROMPTS_NAME = "prompts";
    private static final String PROMPT_NAME_NAME = "name";
    private static final String PROMPT_DESCRIPTION_NAME = "description";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return McpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpOptionsConfig mcpOptions = (McpOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpOptions.prompts != null && !mcpOptions.prompts.isEmpty())
        {
            JsonArrayBuilder prompts = Json.createArrayBuilder();
            for (McpPromptConfig prompt : mcpOptions.prompts)
            {
                JsonObjectBuilder promptObject = Json.createObjectBuilder();
                promptObject.add(PROMPT_NAME_NAME, prompt.name);
                if (prompt.description != null)
                {
                    promptObject.add(PROMPT_DESCRIPTION_NAME, prompt.description);
                }
                prompts.add(promptObject);
            }
            object.add(PROMPTS_NAME, prompts);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        McpOptionsConfigBuilder<McpOptionsConfig> builder = McpOptionsConfig.builder();

        if (object.containsKey(PROMPTS_NAME))
        {
            JsonArray prompts = object.getJsonArray(PROMPTS_NAME);
            for (int i = 0; i < prompts.size(); i++)
            {
                JsonObject prompt = prompts.getJsonObject(i);
                String name = prompt.getString(PROMPT_NAME_NAME);
                String description = prompt.containsKey(PROMPT_DESCRIPTION_NAME)
                    ? prompt.getString(PROMPT_DESCRIPTION_NAME)
                    : null;
                builder.prompt(name, description);
            }
        }

        return builder.build();
    }
}
