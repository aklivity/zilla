/*
 * Copyright 2021-2023 Aklivity Inc
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

package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaBinding;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class MqttKafkaOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String TOPICS_NAME = "topics";
    private static final String SERVER_NAME = "server";
    private static final String SESSIONS_NAME = "sessions";
    private static final String MESSAGES_NAME = "messages";
    private static final String RETAINED_NAME = "retained";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return MqttKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        MqttKafkaOptionsConfig mqttKafkaOptions = (MqttKafkaOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        String serverRef = mqttKafkaOptions.serverRef;
        MqttKafkaTopicsConfig topics = mqttKafkaOptions.topics;

        if (serverRef != null)
        {
            object.add(SERVER_NAME, serverRef);
        }
        if (topics != null)
        {
            JsonObjectBuilder newTopics = Json.createObjectBuilder();
            String16FW sessions = topics.sessions;
            if (sessions != null)
            {
                newTopics.add(SESSIONS_NAME, sessions.asString());
            }

            String16FW messages = topics.messages;
            if (messages != null)
            {
                newTopics.add(MESSAGES_NAME, messages.asString());
            }

            String16FW retained = topics.retained;
            if (retained != null)
            {
                newTopics.add(RETAINED_NAME, retained.asString());
            }

            object.add(TOPICS_NAME, newTopics);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        JsonObject topics = object.getJsonObject(TOPICS_NAME);
        String server = object.getString(SERVER_NAME, null);

        String16FW newSessions = new String16FW(topics.getString(SESSIONS_NAME));
        String16FW newMessages = new String16FW(topics.getString(MESSAGES_NAME));
        String16FW newRetained = new String16FW(topics.getString(RETAINED_NAME));

        MqttKafkaTopicsConfig newTopics = new MqttKafkaTopicsConfig(newSessions, newMessages, newRetained);

        return new MqttKafkaOptionsConfig(newTopics, server);
    }
}
