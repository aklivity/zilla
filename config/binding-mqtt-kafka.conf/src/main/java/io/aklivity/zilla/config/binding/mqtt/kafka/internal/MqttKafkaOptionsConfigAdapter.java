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
package io.aklivity.zilla.config.binding.mqtt.kafka.internal;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaOptionsConfig;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaPublishConfig;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaTopicsConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class MqttKafkaOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String TOPICS_NAME = "topics";
    private static final String CLIENTS_NAME = "clients";
    private static final String SESSIONS_NAME = "sessions";
    private static final String MESSAGES_NAME = "messages";
    private static final String RETAINED_NAME = "retained";
    private static final String PUBLISH_NAME = "publish";
    private static final String QOS_MAX_NAME = "qosMax";
    private static final String QOS_MAX_DEFAULT = "exactly_once";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        MqttKafkaOptionsConfig mqttKafkaOptions = (MqttKafkaOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        MqttKafkaTopicsConfig topics = mqttKafkaOptions.topics;
        List<String> clients = mqttKafkaOptions.clients;

        if (topics != null)
        {
            JsonObjectBuilder newTopics = Json.createObjectBuilder();
            String sessions = topics.sessions;
            if (sessions != null)
            {
                newTopics.add(SESSIONS_NAME, sessions);
            }

            String messages = topics.messages;
            if (messages != null)
            {
                newTopics.add(MESSAGES_NAME, messages);
            }

            String retained = topics.retained;
            if (retained != null)
            {
                newTopics.add(RETAINED_NAME, retained);
            }

            object.add(TOPICS_NAME, newTopics);
        }
        if (clients != null && !clients.isEmpty())
        {
            JsonArrayBuilder clientsBuilder = Json.createArrayBuilder();
            clients.forEach(clientsBuilder::add);
            object.add(CLIENTS_NAME, clientsBuilder.build());
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        MqttKafkaOptionsConfigBuilder<MqttKafkaOptionsConfig> options = MqttKafkaOptionsConfig.builder();
        JsonObject topics = object.getJsonObject(TOPICS_NAME);
        JsonArray clientsJson = object.getJsonArray(CLIENTS_NAME);
        JsonObject publish = object.getJsonObject(PUBLISH_NAME);

        List<String> clients = new ArrayList<>();
        if (clientsJson != null)
        {
            for (int i = 0; i < clientsJson.size(); i++)
            {
                clients.add(clientsJson.getString(i));
            }
        }
        options.clients(clients);

        options.topics(MqttKafkaTopicsConfig.builder()
            .sessions(topics.getString(SESSIONS_NAME))
            .messages(topics.getString(MESSAGES_NAME))
            .retained(topics.getString(RETAINED_NAME))
            .build());

        if (publish != null)
        {
            options.publish(MqttKafkaPublishConfig.builder()
                .qosMax(publish.getString(QOS_MAX_NAME)).build());
        }
        else
        {
            options.publish(MqttKafkaPublishConfig.builder()
                .qosMax(QOS_MAX_DEFAULT).build());
        }

        return options.build();
    }
}
