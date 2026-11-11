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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionConfig;
import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionKind;

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
        assertThat(condition.kind(), equalTo(MqttKafkaConditionKind.SUBSCRIBE));
        assertThat(condition.subscribes, not(nullValue()));
        assertThat(condition.subscribes.size(), equalTo(1));
        assertThat(condition.subscribes.get(0).topic, equalTo("test"));
        assertThat(condition.subscribes.get(0).share, nullValue());
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
        assertThat(condition.kind(), equalTo(MqttKafkaConditionKind.PUBLISH));
        assertThat(condition.publishes, not(nullValue()));
        assertThat(condition.publishes.size(), equalTo(1));
        assertThat(condition.publishes.get(0), equalTo("test"));
    }

    @Test
    public void shouldWriteSubscribeCondition()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("test")
                .build()
            .build();

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
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .publish("test")
            .build();

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

    @Test
    public void shouldReadSubscribeConditionWithShare()
    {
        String text =
            "{" +
                "\"subscribe\":" +
                "[" +
                    "{" +
                        "\"topic\": \"one\"," +
                        "\"share\": \"consumers\"" +
                    "}," +
                    "{" +
                        "\"topic\": \"two\"" +
                    "}" +
                "]" +
            "}";

        MqttKafkaConditionConfig condition = jsonb.fromJson(text, MqttKafkaConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.subscribes.size(), equalTo(2));
        assertThat(condition.subscribes.get(0).topic, equalTo("one"));
        assertThat(condition.subscribes.get(0).share, equalTo("consumers"));
        assertThat(condition.subscribes.get(1).topic, equalTo("two"));
        assertThat(condition.subscribes.get(1).share, nullValue());
    }

    @Test
    public void shouldWriteSubscribeConditionWithShare()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("one")
                .share("consumers")
                .build()
            .subscribe()
                .topic("two")
                .build()
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"subscribe\":" +
                "[" +
                    "{" +
                        "\"topic\":\"one\"," +
                        "\"share\":\"consumers\"" +
                    "}," +
                    "{" +
                        "\"topic\":\"two\"" +
                    "}" +
                "]" +
            "}"));
    }
}
