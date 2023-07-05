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
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.String8FW;

public class MqttKafkaOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String TOPICS_NAME = "topics";
    private static final String SESSIONS_NAME = "sessions";
    private static final String MESSAGES_NAME = "messages";
    private static final String RETAINED_NAME = "retained";

    private static final String8FW SESSIONS_DEFAULT = new String8FW("mqtt_sessions");
    private static final String8FW MESSAGES_DEFAULT = new String8FW("mqtt_messages");
    private static final String8FW RETAINED_DEFAULT = new String8FW("mqtt_retained");

    public static final MqttKafkaTopicsConfig TOPICS_DEFAULT =
        new MqttKafkaTopicsConfig(SESSIONS_DEFAULT, MESSAGES_DEFAULT, RETAINED_DEFAULT);

    public static final MqttKafkaOptionsConfig DEFAULT =
        new MqttKafkaOptionsConfig(TOPICS_DEFAULT);

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

        MqttKafkaTopicsConfig topics = mqttKafkaOptions.topics;

        if (topics != null &&
            !(TOPICS_DEFAULT.equals(topics)))
        {
            JsonObjectBuilder newTopics = Json.createObjectBuilder();
            String8FW sessions = topics.sessions;
            if (sessions != null &&
                !(SESSIONS_DEFAULT.equals(sessions)))
            {
                newTopics.add(SESSIONS_NAME, sessions.asString());
            }

            String8FW messages = topics.messages;
            if (messages != null &&
                !(MESSAGES_DEFAULT.equals(messages)))
            {
                newTopics.add(MESSAGES_NAME, messages.asString());
            }

            String8FW retained = topics.retained;
            if (retained != null &&
                !(RETAINED_DEFAULT.equals(retained)))
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
        MqttKafkaTopicsConfig newTopics = TOPICS_DEFAULT;

        if (object.containsKey(TOPICS_NAME))
        {
            JsonObject topics = object.getJsonObject(TOPICS_NAME);
            String8FW newSessions = SESSIONS_DEFAULT;

            if (topics.containsKey(SESSIONS_NAME))
            {
                newSessions = new String8FW(topics.getString(SESSIONS_NAME));
            }

            String8FW newMessages = MESSAGES_DEFAULT;

            if (topics.containsKey(MESSAGES_NAME))
            {
                newMessages = new String8FW(topics.getString(MESSAGES_NAME));
            }

            String8FW newRetained = RETAINED_DEFAULT;

            if (topics.containsKey(RETAINED_NAME))
            {
                newRetained = new String8FW(topics.getString(RETAINED_NAME));
            }

            newTopics = new MqttKafkaTopicsConfig(newSessions, newMessages, newRetained);
        }

        return new MqttKafkaOptionsConfig(newTopics);
    }
}
