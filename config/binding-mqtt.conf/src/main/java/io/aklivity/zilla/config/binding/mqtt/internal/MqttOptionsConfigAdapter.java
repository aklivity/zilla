/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.config.binding.mqtt.internal;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.mqtt.MqttAuthorizationConfig;
import io.aklivity.zilla.config.binding.mqtt.MqttAuthorizationConfigBuilder;
import io.aklivity.zilla.config.binding.mqtt.MqttCredentialsConfig;
import io.aklivity.zilla.config.binding.mqtt.MqttCredentialsConfigBuilder;
import io.aklivity.zilla.config.binding.mqtt.MqttOptionsConfig;
import io.aklivity.zilla.config.binding.mqtt.MqttOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.mqtt.MqttPatternConfig;
import io.aklivity.zilla.config.binding.mqtt.MqttTopicConfig;
import io.aklivity.zilla.config.binding.mqtt.MqttVersion;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class MqttOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_CONNECT_NAME = "connect";
    private static final String TOPICS_NAME = "topics";
    private static final String VERSIONS_NAME = "versions";
    private static final String STORE_NAME = "store";
    private static final String SERVER_NAME = "server";

    private final MqttTopicConfigAdapter mqttTopic = new MqttTopicConfigAdapter();

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

        if (mqttOptions.topics != null)
        {
            JsonArrayBuilder topics = Json.createArrayBuilder();
            mqttOptions.topics.stream()
                .map(mqttTopic::adaptToJson)
                .forEach(topics::add);
            object.add(TOPICS_NAME, topics);
        }

        if (mqttOptions.versions != null)
        {
            JsonArrayBuilder versions = Json.createArrayBuilder();
            mqttOptions.versions.forEach(v -> versions.add(v.specification()));
            object.add(VERSIONS_NAME, versions);
        }

        String store = mqttOptions.store;
        if (store != null)
        {
            object.add(STORE_NAME, store);
        }

        String server = mqttOptions.server;
        if (server != null)
        {
            object.add(SERVER_NAME, server);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        MqttOptionsConfigBuilder<MqttOptionsConfig> mqttOptions = MqttOptionsConfig.builder();

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            MqttAuthorizationConfigBuilder<?> mqttAuthorization = mqttOptions.authorization();

            JsonObject authorizations = object.getJsonObject(AUTHORIZATION_NAME);

            for (String name : authorizations.keySet())
            {
                JsonObject authorization = authorizations.getJsonObject(name);
                JsonObject credentials = authorization.getJsonObject(AUTHORIZATION_CREDENTIALS_NAME);
                if (credentials != null)
                {
                    MqttCredentialsConfigBuilder<?> mqttCredentials = mqttAuthorization
                        .name(name)
                        .credentials();

                    if (credentials.containsKey(AUTHORIZATION_CREDENTIALS_CONNECT_NAME))
                    {
                        credentials.getJsonObject(AUTHORIZATION_CREDENTIALS_CONNECT_NAME)
                            .forEach((n, v) -> mqttCredentials.connect()
                                .property(MqttPatternConfig.MqttConnectProperty.ofName(n))
                                .pattern(JsonString.class.cast(v).getString())
                                .build());
                    }

                    mqttCredentials.build();
                }
            }

            mqttAuthorization.build();
        }

        if (object.containsKey(TOPICS_NAME))
        {
            List<MqttTopicConfig> topics = object.getJsonArray(TOPICS_NAME).stream()
                .map(item -> mqttTopic.adaptFromJson((JsonObject) item))
                .collect(Collectors.toList());
            mqttOptions.topics(topics);
        }

        if (object.containsKey(VERSIONS_NAME))
        {
            List<MqttVersion> versions = object.getJsonArray(VERSIONS_NAME).stream()
                .map(item -> MqttVersion.ofSpecification(((JsonString) item).getString()))
                .collect(Collectors.toList());
            mqttOptions.versions(versions);
        }

        if (object.containsKey(STORE_NAME))
        {
            mqttOptions.store(object.getString(STORE_NAME));
        }

        if (object.containsKey(SERVER_NAME))
        {
            mqttOptions.server(object.getString(SERVER_NAME));
        }

        return mqttOptions.build();
    }
}
