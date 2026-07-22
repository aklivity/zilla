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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaTopicsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class MqttKafkaOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new MqttKafkaOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            """
            topics:
              sessions: sessions
              messages: messages
              retained: retained
            clients:
              - /clients/{identity}/#
              - /department/clients/{identity}/#
            """;

        MqttKafkaOptionsConfig options = jsonb.fromJson(text, MqttKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.topics, not(nullValue()));
        assertThat(options.topics.sessions, equalTo("sessions"));
        assertThat(options.topics.messages, equalTo("messages"));
        assertThat(options.topics.retained, equalTo("retained"));
        assertThat(options.clients, not(nullValue()));
        assertThat(options.clients.size(), equalTo(2));
        assertThat(options.clients.get(0), equalTo("/clients/{identity}/#"));
        assertThat(options.clients.get(1), equalTo("/department/clients/{identity}/#"));
    }

    @Test
    public void shouldWriteOptions()
    {
        MqttKafkaOptionsConfig options = MqttKafkaOptionsConfig.builder()
            .topics(MqttKafkaTopicsConfig.builder()
                .sessions("sessions")
                .messages("messages")
                .retained("retained")
                .build())
            .clients(Arrays.asList("/clients/{identity}/#", "/department/clients/{identity}/#"))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            """
            topics:
              sessions: sessions
              messages: messages
              retained: retained
            clients:
              - "/clients/{identity}/#"
              - "/department/clients/{identity}/#"
            """));
    }

    @Test
    public void shouldReadOptionsWithoutClients()
    {
        String text =
            """
            topics:
              sessions: sessions
              messages: messages
              retained: retained
            """;

        MqttKafkaOptionsConfig options = jsonb.fromJson(text, MqttKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.topics, not(nullValue()));
        assertThat(options.topics.sessions, equalTo("sessions"));
        assertThat(options.topics.messages, equalTo("messages"));
        assertThat(options.topics.retained, equalTo("retained"));
    }

    @Test
    public void shouldWriteOptionsWithoutClients()
    {
        MqttKafkaOptionsConfig options = MqttKafkaOptionsConfig.builder()
            .topics(MqttKafkaTopicsConfig.builder()
                .sessions("sessions")
                .messages("messages")
                .retained("retained")
                .build())
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            """
            topics:
              sessions: sessions
              messages: messages
              retained: retained
            """));
    }
}
