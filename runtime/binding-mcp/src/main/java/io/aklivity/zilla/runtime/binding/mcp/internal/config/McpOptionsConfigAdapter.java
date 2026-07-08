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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.config.McpElicitationConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpElicitationConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpBinding;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String ELICITATION_NAME = "elicitation";
    private static final String ELICITATION_CALLBACK_NAME = "callback";
    private static final String ELICITATION_TIMEOUT_NAME = "timeout";

    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_REALM_NAME = "realm";

    private static final String CACHE_NAME = "cache";
    private static final String CACHE_STORE_NAME = "store";
    private static final String CACHE_TTL_NAME = "ttl";
    private static final String CACHE_TTL_DEFAULT = "PT5M";
    private static final String CACHE_AUTHORIZATION_NAME = "authorization";
    private static final String CACHE_AUTHORIZATION_CREDENTIALS_NAME = "credentials";

    private static final String SERVER_NAME = "server";

    private static final String TOOLS_NAME = "tools";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

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

        if (mcpOptions.elicitation != null)
        {
            JsonObjectBuilder elicitation = Json.createObjectBuilder();
            elicitation.add(ELICITATION_CALLBACK_NAME, mcpOptions.elicitation.callback);
            if (mcpOptions.elicitation.timeout != null)
            {
                elicitation.add(ELICITATION_TIMEOUT_NAME, mcpOptions.elicitation.timeout.toString());
            }
            object.add(ELICITATION_NAME, elicitation);
        }

        if (mcpOptions.authorization != null && mcpOptions.authorization.name != null)
        {
            JsonObjectBuilder authorization = Json.createObjectBuilder();
            JsonObjectBuilder guardObject = Json.createObjectBuilder();
            if (mcpOptions.authorization.credentials != null)
            {
                guardObject.add(AUTHORIZATION_CREDENTIALS_NAME, mcpOptions.authorization.credentials);
            }
            if (mcpOptions.authorization.realm != null)
            {
                guardObject.add(AUTHORIZATION_REALM_NAME, mcpOptions.authorization.realm);
            }
            authorization.add(mcpOptions.authorization.name, guardObject);
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

        if (mcpOptions.server != null)
        {
            object.add(SERVER_NAME, mcpOptions.server);
        }

        if (mcpOptions.tools != null)
        {
            model.adaptType(mcpOptions.tools.model);
            object.add(TOOLS_NAME, model.adaptToJson(mcpOptions.tools));
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        McpOptionsConfigBuilder<McpOptionsConfig> builder = McpOptionsConfig.builder();

        if (object.containsKey(ELICITATION_NAME))
        {
            JsonObject elicitation = object.getJsonObject(ELICITATION_NAME);
            String callback = elicitation.containsKey(ELICITATION_CALLBACK_NAME)
                ? elicitation.getString(ELICITATION_CALLBACK_NAME)
                : McpElicitationConfig.DEFAULT_CALLBACK_PATH;
            McpElicitationConfigBuilder<McpOptionsConfigBuilder<McpOptionsConfig>> elicitationBuilder = builder.elicitation()
                .callback(callback);
            if (elicitation.containsKey(ELICITATION_TIMEOUT_NAME))
            {
                elicitationBuilder.timeout(Duration.parse(elicitation.getString(ELICITATION_TIMEOUT_NAME)));
            }
            elicitationBuilder.build();
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            JsonObject authorization = object.getJsonObject(AUTHORIZATION_NAME);
            authorization.forEach((guard, value) ->
            {
                JsonObject guardObject = (JsonObject) value;
                String credentials = guardObject.containsKey(AUTHORIZATION_CREDENTIALS_NAME)
                    ? ((JsonString) guardObject.get(AUTHORIZATION_CREDENTIALS_NAME)).getString()
                    : null;
                String realm = guardObject.containsKey(AUTHORIZATION_REALM_NAME)
                    ? ((JsonString) guardObject.get(AUTHORIZATION_REALM_NAME)).getString()
                    : null;
                builder.authorization()
                    .name(guard)
                    .credentials(credentials)
                    .realm(realm)
                    .build();
            });
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

        if (object.containsKey(SERVER_NAME))
        {
            builder.server(object.getString(SERVER_NAME));
        }

        if (object.containsKey(TOOLS_NAME))
        {
            JsonValue tools = object.get(TOOLS_NAME);
            ModelConfig toolsModel = model.adaptFromJson(tools);
            builder.tools(toolsModel);
        }

        return builder.build();
    }
}
