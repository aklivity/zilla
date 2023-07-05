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

import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.String8FW;

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
                "\"topics\":" +
                "{" +
                "\"sessions\":\"mqtt_sessions\"," +
                "\"messages\":\"mqtt_messages\"," +
                "\"retained\":\"mqtt_retained\"," +
                "}" +
                "}";

        MqttKafkaOptionsConfig options = jsonb.fromJson(text, MqttKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.topics, not(nullValue()));
        assertThat(options.topics.sessions.asString(), equalTo("mqtt_sessions"));
        assertThat(options.topics.messages.asString(), equalTo("mqtt_messages"));
        assertThat(options.topics.retained.asString(), equalTo("mqtt_retained"));
    }

    @Test
    public void shouldWriteOptions()
    {
        MqttKafkaOptionsConfig options = new MqttKafkaOptionsConfig(
            new MqttKafkaTopicsConfig(
                new String8FW("sessions"),
                new String8FW("messages"),
                new String8FW("retained")));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"topics\":" +
                "{" +
                "\"sessions\":\"sessions\"," +
                "\"messages\":\"messages\"," +
                "\"retained\":\"retained\"" +
                "}" +
                "}"));
    }
}
