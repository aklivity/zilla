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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import java.time.Duration;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.config.McpAuthorizationConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.config.McpElicitationConfig;
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

    private static final String ELICITATION_NAME = "elicitation";
    private static final String ELICITATION_CALLBACK_NAME = "callback";

    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_NAME_NAME = "name";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";

    private static final String CACHE_NAME = "cache";
    private static final String CACHE_STORE_NAME = "store";
    private static final String CACHE_TTL_NAME = "ttl";
    private static final String CACHE_TTL_DEFAULT = "PT5M";
    private static final String CACHE_AUTHORIZATION_NAME = "authorization";
    private static final String CACHE_AUTHORIZATION_CREDENTIALS_NAME = "credentials";

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

        if (mcpOptions.elicitation != null)
        {
            JsonObjectBuilder elicitation = Json.createObjectBuilder();
            elicitation.add(ELICITATION_CALLBACK_NAME, mcpOptions.elicitation.callback);
            object.add(ELICITATION_NAME, elicitation);
        }

        if (mcpOptions.authorization != null)
        {
            JsonObjectBuilder authorization = Json.createObjectBuilder();
            if (mcpOptions.authorization.name != null)
            {
                authorization.add(AUTHORIZATION_NAME_NAME, mcpOptions.authorization.name);
            }
            if (mcpOptions.authorization.credentials != null)
            {
                authorization.add(AUTHORIZATION_CREDENTIALS_NAME, mcpOptions.authorization.credentials);
            }
            object.add(AUTHORIZATION_NAME, authorization);
        }

        if (mcpOptions.cache != null)
        {
            JsonObjectBuilder cache = Json.createObjectBuilder();
            McpCacheConfig cacheConfig = mcpOptions.cache;
            cache.add(CACHE_STORE_NAME, cacheConfig.store);

            if (cacheConfig.ttl != null)
            {
                cache.add(CACHE_TTL_NAME, cacheConfig.ttl.toString());
            }

            if (cacheConfig.authorization != null)
            {
                JsonObjectBuilder authorization = Json.createObjectBuilder();
                JsonObjectBuilder guardObject = Json.createObjectBuilder();
                if (cacheConfig.authorization.credentials != null)
                {
                    guardObject.add(CACHE_AUTHORIZATION_CREDENTIALS_NAME, cacheConfig.authorization.credentials);
                }
                authorization.add(cacheConfig.authorization.name, guardObject);
                cache.add(CACHE_AUTHORIZATION_NAME, authorization);
            }

            object.add(CACHE_NAME, cache);
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

        if (object.containsKey(ELICITATION_NAME))
        {
            JsonObject elicitation = object.getJsonObject(ELICITATION_NAME);
            String callback = elicitation.containsKey(ELICITATION_CALLBACK_NAME)
                ? elicitation.getString(ELICITATION_CALLBACK_NAME)
                : McpElicitationConfig.DEFAULT_CALLBACK_PATH;
            builder.elicitation()
                .callback(callback)
                .build();
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            JsonObject authorization = object.getJsonObject(AUTHORIZATION_NAME);
            McpAuthorizationConfigBuilder<McpOptionsConfigBuilder<McpOptionsConfig>> authorizationBuilder =
                builder.authorization();
            if (authorization.containsKey(AUTHORIZATION_NAME_NAME))
            {
                authorizationBuilder.name(authorization.getString(AUTHORIZATION_NAME_NAME));
            }
            if (authorization.containsKey(AUTHORIZATION_CREDENTIALS_NAME))
            {
                authorizationBuilder.credentials(authorization.getString(AUTHORIZATION_CREDENTIALS_NAME));
            }
            authorizationBuilder.build();
        }

        if (object.containsKey(CACHE_NAME))
        {
            JsonObject cache = object.getJsonObject(CACHE_NAME);
            McpCacheConfigBuilder<McpOptionsConfigBuilder<McpOptionsConfig>> cacheBuilder = builder.cache()
                .store(cache.getString(CACHE_STORE_NAME));

            cacheBuilder.ttl(Duration.parse(cache.containsKey(CACHE_TTL_NAME)
                ? cache.getString(CACHE_TTL_NAME)
                : CACHE_TTL_DEFAULT));

            if (cache.containsKey(CACHE_AUTHORIZATION_NAME))
            {
                JsonObject authorization = cache.getJsonObject(CACHE_AUTHORIZATION_NAME);
                authorization.forEach((guard, value) ->
                {
                    JsonObject guardObject = (JsonObject) value;
                    String credentials = guardObject.containsKey(CACHE_AUTHORIZATION_CREDENTIALS_NAME)
                        ? ((JsonString) guardObject.get(CACHE_AUTHORIZATION_CREDENTIALS_NAME)).getString()
                        : null;
                    cacheBuilder.authorization()
                        .name(guard)
                        .credentials(credentials)
                        .build();
                });
            }

            cacheBuilder.build();
        }

        return builder.build();
    }
}
