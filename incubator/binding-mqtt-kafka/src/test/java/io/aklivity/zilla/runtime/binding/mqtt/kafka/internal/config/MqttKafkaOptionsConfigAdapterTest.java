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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;

public class MqttKafkaOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new MqttKafkaOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            "{" +
                "\"server\":\"mqtt-1.example.com:1883\"," +
                "\"topics\":" +
                "{" +
                "\"sessions\":\"sessions\"," +
                "\"messages\":\"messages\"," +
                "\"retained\":\"retained\"," +
                "}" +
                "}";

        MqttKafkaOptionsConfig options = jsonb.fromJson(text, MqttKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.topics, not(nullValue()));
        assertThat(options.topics.sessions.asString(), equalTo("sessions"));
        assertThat(options.topics.messages.asString(), equalTo("messages"));
        assertThat(options.topics.retained.asString(), equalTo("retained"));
        assertThat(options.serverRef, equalTo("mqtt-1.example.com:1883"));
    }

    @Test
    public void shouldWriteOptions()
    {
        MqttKafkaOptionsConfig options = new MqttKafkaOptionsConfig(
            new MqttKafkaTopicsConfig(
                new String16FW("sessions"),
                new String16FW("messages"),
                new String16FW("retained")), "mqtt-1.example.com:1883");

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"server\":\"mqtt-1.example.com:1883\"," +
                "\"topics\":" +
                "{" +
                "\"sessions\":\"sessions\"," +
                "\"messages\":\"messages\"," +
                "\"retained\":\"retained\"" +
                "}" +
                "}"));
    }
}
