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
package io.aklivity.zilla.config.binding.mcp.kafka.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.kafka.KafkaTopicConfig;
import io.aklivity.zilla.config.binding.kafka.KafkaTopicConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.kafka.McpKafkaOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.kafka.McpKafkaOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpKafkaOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
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
    private static final String TOPICS_NAME = "topics";
    private static final String TOPIC_NAME_NAME = "name";
    private static final String TOPIC_KEY_NAME = "key";
    private static final String TOPIC_VALUE_NAME = "value";
    private static final String TOPIC_SUBJECT_NAME = "subject";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

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

        if (mcpKafkaOptions.topics != null &&
            !mcpKafkaOptions.topics.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            mcpKafkaOptions.topics.forEach(t -> entries.add(topicToJson(t)));

            object.add(TOPICS_NAME, entries);
        }

        return object.build();
    }

    private JsonObject topicToJson(
        KafkaTopicConfig topic)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (topic.name != null)
        {
            object.add(TOPIC_NAME_NAME, topic.name);
        }

        if (topic.key != null)
        {
            model.adaptType(topic.key.model);
            object.add(TOPIC_KEY_NAME, model.adaptToJson(topic.key));
        }

        if (topic.value != null)
        {
            model.adaptType(topic.value.model);
            object.add(TOPIC_VALUE_NAME, model.adaptToJson(topic.value));
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

        if (object.containsKey(TOPICS_NAME))
        {
            object.getJsonArray(TOPICS_NAME).stream()
                .map(JsonObject.class::cast)
                .forEach(t -> options.topic(topicFromJson(t)));
        }

        return options.build();
    }

    private KafkaTopicConfig topicFromJson(
        JsonObject object)
    {
        String name = object.containsKey(TOPIC_NAME_NAME)
            ? object.getString(TOPIC_NAME_NAME)
            : null;

        KafkaTopicConfigBuilder<KafkaTopicConfig> topic = KafkaTopicConfig.builder();
        topic.name(name);

        if (object.containsKey(TOPIC_KEY_NAME))
        {
            JsonObjectBuilder keyObject = Json.createObjectBuilder(object.getJsonObject(TOPIC_KEY_NAME));
            keyObject.add(TOPIC_SUBJECT_NAME, name + "-key");

            topic.key(model.adaptFromJson(keyObject.build()));
        }

        if (object.containsKey(TOPIC_VALUE_NAME))
        {
            JsonObjectBuilder valueObject = Json.createObjectBuilder(object.getJsonObject(TOPIC_VALUE_NAME));
            valueObject.add(TOPIC_SUBJECT_NAME, name + "-value");

            topic.value(model.adaptFromJson(valueObject.build()));
        }

        return topic.build();
    }
}
