/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicParamConfig;

public class MqttConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new MqttConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
            "{" +
                "\"session\":" +
                "[" +
                    "{" +
                        "\"client-id\": \"client-1\"" +
                    "}" +
                "]," +
                "\"subscribe\":" +
                "[" +
                    "{" +
                        "\"topic\": \"reply/one\"" +
                    "}," +
                    "{" +
                        "\"topic\": \"reply/two\"" +
                    "}," +
                    "{" +
                        "\"topic\": \"reply/{id}\"," +
                        "\"params\": {" +
                            "\"id\": \"${guarded['jwt'].identity}\"" +
                        "}" +
                    "}," +
                "]," +
                "\"publish\":" +
                "[" +
                    "{" +
                        "\"topic\": \"command/one\"" +
                    "}," +
                    "{" +
                        "\"topic\": \"command/two\"" +
                    "}," +
                    "{" +
                        "\"topic\": \"command/{id}\"," +
                        "\"params\": {" +
                            "\"id\": \"${guarded['jwt'].identity}\"" +
                        "}" +
                    "}," +
                "]" +
            "}";

        MqttConditionConfig condition = jsonb.fromJson(text, MqttConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.sessions, not(nullValue()));
        assertThat(condition.sessions.get(0).clientId, equalTo("client-1"));
        assertThat(condition.subscribes, not(nullValue()));
        assertThat(condition.subscribes.get(0).topic, equalTo("reply/one"));
        assertThat(condition.subscribes.get(1).topic, equalTo("reply/two"));
        assertThat(condition.subscribes.get(2).topic, equalTo("reply/{id}"));
        assertThat(condition.subscribes.get(2).params.get(0).name, equalTo("id"));
        assertThat(condition.subscribes.get(2).params.get(0).value, equalTo("${guarded['jwt'].identity}"));
        assertThat(condition.publishes, not(nullValue()));
        assertThat(condition.publishes.get(0).topic, equalTo("command/one"));
        assertThat(condition.publishes.get(1).topic, equalTo("command/two"));
        assertThat(condition.publishes.get(2).topic, equalTo("command/{id}"));
        assertThat(condition.publishes.get(2).params.get(0).name, equalTo("id"));
        assertThat(condition.publishes.get(2).params.get(0).value, equalTo("${guarded['jwt'].identity}"));
    }

    @Test
    public void shouldWriteCondition()
    {
        MqttConditionConfig condition = MqttConditionConfig.builder()
            .inject(identity())
            .session()
                .clientId("client-1")
                .build()
            .subscribe()
                .topic("reply/one")
                .build()
            .subscribe()
                .topic("reply/two")
                .build()
            .subscribe()
                .topic("reply/{id}")
                .param(MqttTopicParamConfig.builder()
                    .name("id")
                    .value("${guarded['jwt'].identity}")
                    .build())
                .build()
            .publish()
                .topic("command/one")
                .build()
            .publish()
                .topic("command/two")
                .build()
            .publish()
                .topic("command/{id}")
                .param(MqttTopicParamConfig.builder()
                    .name("id")
                    .value("${guarded['jwt'].identity}")
                    .build())
                .build()
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("""
            {
                "session":
                [
                    {
                        "client-id":"client-1"
                    }
                ],
                "subscribe":
                [
                    {
                        "topic":"reply/one"
                    },
                    {
                        "topic":"reply/two"
                    },
                    {
                        "topic":"reply/{id}",
                        "params":
                        {
                            "id":"${guarded['jwt'].identity}"
                        }
                    }
                ],
                "publish":
                [
                    {
                        "topic":"command/one"
                    },
                    {
                        "topic":"command/two"
                    },
                    {
                        "topic":"command/{id}",
                        "params":
                        {
                            "id":"${guarded['jwt'].identity}"
                        }
                    }
                ]
            }
            """.replaceAll("\\s*\\n\\s*", "")));
    }
}
