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

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttAuthorizationConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttCredentialsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttCredentialsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.MqttBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class MqttOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_CONNECT_NAME = "connect";
    private static final String TOPICS_NAME = "topics";
    private static final String VERSIONS_NAME = "versions";

    private final MqttTopicConfigAdapter mqttTopic = new MqttTopicConfigAdapter();

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
            mqttOptions.versions
                .forEach(versions::add);
            object.add(VERSIONS_NAME, versions);
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
            List<Integer> versions = object.getJsonArray(VERSIONS_NAME).stream()
                .map(item -> ((JsonNumber) item).intValue())
                .collect(Collectors.toList());
            mqttOptions.versions(versions);
        }

        return mqttOptions.build();
    }
}
