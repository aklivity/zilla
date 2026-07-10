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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class McpKafkaOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final Pattern SERVER_PATTERN = Pattern.compile("([^\\:]+):(\\d+)");
    private static final String SERVERS_NAME = "servers";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String CREDENTIALS_MECHANISM_NAME = "mechanism";
    private static final String CREDENTIALS_USERNAME_NAME = "username";
    private static final String CREDENTIALS_PASSWORD_NAME = "password";
    private static final String CREDENTIALS_TOKEN_NAME = "token";
    private static final String OAUTHBEARER_MECHANISM = "oauthbearer";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return McpKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        McpKafkaOptionsConfig mcpKafkaOptions = (McpKafkaOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpKafkaOptions.servers != null &&
            !mcpKafkaOptions.servers.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            mcpKafkaOptions.servers.forEach(s -> entries.add(String.format("%s:%d", s.host, s.port)));

            object.add(SERVERS_NAME, entries);
        }

        if (mcpKafkaOptions.authorization != null)
        {
            final String mechanism = mcpKafkaOptions.authorization.credentials.mechanism;
            JsonObjectBuilder credentials = Json.createObjectBuilder();
            credentials.add(CREDENTIALS_MECHANISM_NAME, mechanism);
            if (OAUTHBEARER_MECHANISM.equals(mechanism))
            {
                credentials.add(CREDENTIALS_TOKEN_NAME, mcpKafkaOptions.authorization.credentials.token);
            }
            else
            {
                credentials.add(CREDENTIALS_USERNAME_NAME, mcpKafkaOptions.authorization.credentials.username);
                credentials.add(CREDENTIALS_PASSWORD_NAME, mcpKafkaOptions.authorization.credentials.password);
            }

            JsonObjectBuilder authorization = Json.createObjectBuilder();
            authorization.add(AUTHORIZATION_CREDENTIALS_NAME, credentials);

            JsonObjectBuilder authorizations = Json.createObjectBuilder();
            authorizations.add(mcpKafkaOptions.authorization.name, authorization);

            object.add(AUTHORIZATION_NAME, authorizations);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        McpKafkaOptionsConfigBuilder<McpKafkaOptionsConfig> options = McpKafkaOptionsConfig.builder();

        if (object.containsKey(SERVERS_NAME))
        {
            object.getJsonArray(SERVERS_NAME).stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(SERVER_PATTERN::matcher)
                .filter(Matcher::matches)
                .forEach(m -> options
                    .server()
                        .host(m.group(1))
                        .port(Integer.parseInt(m.group(2)))
                        .build());
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            JsonObject authorizations = object.getJsonObject(AUTHORIZATION_NAME);
            String guardName = authorizations.keySet().iterator().next();
            JsonObject authorization = authorizations.getJsonObject(guardName);
            JsonObject credentials = authorization.getJsonObject(AUTHORIZATION_CREDENTIALS_NAME);
            String mechanism = credentials.getString(CREDENTIALS_MECHANISM_NAME);
            if (OAUTHBEARER_MECHANISM.equals(mechanism))
            {
                options.authorization()
                    .name(guardName)
                    .credentials()
                        .mechanism(mechanism)
                        .token(credentials.getString(CREDENTIALS_TOKEN_NAME))
                        .build()
                    .build();
            }
            else
            {
                options.authorization()
                    .name(guardName)
                    .credentials()
                        .mechanism(mechanism)
                        .username(credentials.getString(CREDENTIALS_USERNAME_NAME))
                        .password(credentials.getString(CREDENTIALS_PASSWORD_NAME))
                        .build()
                    .build();
            }
        }

        return options.build();
    }
}
