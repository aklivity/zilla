/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttBinding;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttAuthorizationConfig.MqttCredentialsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttAuthorizationConfig.MqttPatternConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfigAdapter;

public class MqttOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_CONNECT_NAME = "connect";
    private static final String AUTHORIZATION_CREDENTIALS_USERNAME_NAME = "username";
    private static final String AUTHORIZATION_CREDENTIALS_PASSWORD_NAME = "password";
    private static final String TOPICS_NAME = "topics";
    private static final String TOPIC_NAME_NAME = "name";
    private static final String TOPIC_CONTENT_NAME = "content";

    private final ValidatorConfigAdapter validator = new ValidatorConfigAdapter();

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return MqttBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        MqttOptionsConfig mqttOptions = (MqttOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        MqttAuthorizationConfig mqttAuthorization = mqttOptions.authorization;
        if (mqttAuthorization != null)
        {
            JsonObjectBuilder authorizations = Json.createObjectBuilder();

            JsonObjectBuilder authorization = Json.createObjectBuilder();

            MqttCredentialsConfig mqttCredentials = mqttAuthorization.credentials;
            if (mqttCredentials != null)
            {
                JsonObjectBuilder credentials = Json.createObjectBuilder();

                if (mqttCredentials.connect != null)
                {
                    JsonObjectBuilder connect = Json.createObjectBuilder();

                    mqttCredentials.connect.forEach(p -> connect.add(p.property.name().toLowerCase(), p.pattern));

                    credentials.add(AUTHORIZATION_CREDENTIALS_CONNECT_NAME, connect);
                }

                authorization.add(AUTHORIZATION_CREDENTIALS_NAME, credentials);

                authorizations.add(mqttAuthorization.name, authorization);
            }

            object.add(AUTHORIZATION_NAME, authorizations);
        }

        List<MqttTopicConfig> mqttTopics = mqttOptions.topics;
        if (mqttTopics != null)
        {
            JsonArrayBuilder topics = Json.createArrayBuilder();

            for (MqttTopicConfig topic : mqttTopics)
            {
                JsonObjectBuilder topicJson = Json.createObjectBuilder();

                topicJson.add(TOPIC_NAME_NAME, topic.name);
                validator.adaptType(topic.content.type);
                JsonValue content = validator.adaptToJson(topic.content);
                topicJson.add(TOPIC_CONTENT_NAME, content);
                topics.add(topicJson);
            }

            object.add(TOPICS_NAME, topics);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        MqttAuthorizationConfig newAuthorization = null;

        JsonObject authorizations = object.containsKey(AUTHORIZATION_NAME)
            ? object.getJsonObject(AUTHORIZATION_NAME)
            : null;

        if (authorizations != null)
        {
            for (String name : authorizations.keySet())
            {
                JsonObject authorization = authorizations.getJsonObject(name);

                MqttCredentialsConfig newCredentials = null;

                JsonObject credentials = authorization.getJsonObject(AUTHORIZATION_CREDENTIALS_NAME);

                if (credentials != null)
                {
                    List<MqttPatternConfig> newConnect =
                        adaptPatternFromJson(credentials, AUTHORIZATION_CREDENTIALS_CONNECT_NAME);

                    newCredentials = new MqttCredentialsConfig(newConnect);
                }

                newAuthorization = new MqttAuthorizationConfig(name, newCredentials);
            }
        }

        List<MqttTopicConfig> newTopics = null;

        JsonArray topicsJson = object.containsKey(TOPICS_NAME)
            ? object.getJsonArray(TOPICS_NAME)
            : null;

        if (topicsJson != null)
        {
            List<MqttTopicConfig> topics = new ArrayList<>();
            for (int i = 0; i < topicsJson.size(); i++)
            {
                JsonObject topic = topicsJson.getJsonObject(i);

                String topicName = topic.getString(TOPIC_NAME_NAME);

                ValidatorConfig content = null;
                if (topic.containsKey(TOPIC_CONTENT_NAME))
                {
                    JsonValue contentJson = topic.get(TOPIC_CONTENT_NAME);
                    content = validator.adaptFromJson(contentJson);
                }
                topics.add(new MqttTopicConfig(topicName, content));
            }
            newTopics = topics;
        }

        return new MqttOptionsConfig(newAuthorization, newTopics);
    }

    private List<MqttPatternConfig> adaptPatternFromJson(
        JsonObject object,
        String property)
    {
        List<MqttPatternConfig> newPatterns = null;
        if (object.containsKey(property))
        {
            newPatterns = new ArrayList<>();

            JsonObject patterns = object.getJsonObject(property);
            for (String name : patterns.keySet())
            {
                name = name.toLowerCase();
                if (name.equals(AUTHORIZATION_CREDENTIALS_USERNAME_NAME) ||
                    name.equals(AUTHORIZATION_CREDENTIALS_PASSWORD_NAME))
                {
                    String pattern = patterns.getString(name);
                    newPatterns.add(new MqttPatternConfig(MqttAuthorizationConfig.MqttConnectProperty.ofName(name), pattern));
                }
            }
        }
        return newPatterns;
    }
}
