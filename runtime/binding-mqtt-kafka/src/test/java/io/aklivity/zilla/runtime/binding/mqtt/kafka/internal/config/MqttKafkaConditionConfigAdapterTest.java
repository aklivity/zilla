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

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionKind;

public class MqttKafkaConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new MqttKafkaConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadSubscribeCondition()
    {
        String text =
            "{" +
                "\"subscribe\":" +
                "[" +
                    "{" +
                        "\"topic\": \"test\"" +
                    "}" +
                "]" +
            "}";

        MqttKafkaConditionConfig condition = jsonb.fromJson(text, MqttKafkaConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.topics, not(nullValue()));
        assertThat(condition.topics.size(), equalTo(1));
        assertThat(condition.topics.get(0), equalTo("test"));
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
            "{" +
                "\"publish\":" +
                    "[" +
                        "{" +
                            "\"topic\": \"test\"" +
                        "}" +
                    "]" +
            "}";

        MqttKafkaConditionConfig condition = jsonb.fromJson(text, MqttKafkaConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.topics, not(nullValue()));
        assertThat(condition.topics.size(), equalTo(1));
        assertThat(condition.topics.get(0), equalTo("test"));
    }

    @Test
    public void shouldWriteSubscribeCondition()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig(List.of("test"), MqttKafkaConditionKind.SUBSCRIBE);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"subscribe\":" +
                "[" +
                    "{" +
                        "\"topic\":\"test\"" +
                    "}" +
                "]" +
            "}"));
    }

    @Test
    public void shouldWritePublishCondition()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig(List.of("test"), MqttKafkaConditionKind.PUBLISH);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                        "\"publish\":" +
                        "[" +
                            "{" +
                                "\"topic\":\"test\"" +
                            "}" +
                        "]" +
                "}"));
    }
}
