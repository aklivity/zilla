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
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPublishConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttSessionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttSubscribeConfig;

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
                        "\"client-id\": \"*\"" +
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
                "]," +
                "\"publish\":" +
                "[" +
                    "{" +
                        "\"topic\": \"command/one\"" +
                    "}," +
                    "{" +
                        "\"topic\": \"command/two\"" +
                    "}" +
                "]" +
            "}";

        MqttConditionConfig condition = jsonb.fromJson(text, MqttConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.sessions, not(nullValue()));
        assertThat(condition.sessions.get(0).clientId, equalTo("*"));
        assertThat(condition.subscribes, not(nullValue()));
        assertThat(condition.subscribes.get(0).topic, equalTo("reply/one"));
        assertThat(condition.subscribes.get(1).topic, equalTo("reply/two"));
        assertThat(condition.publishes, not(nullValue()));
        assertThat(condition.publishes.get(0).topic, equalTo("command/one"));
        assertThat(condition.publishes.get(1).topic, equalTo("command/two"));
    }

    @Test
    public void shouldWriteCondition()
    {
        MqttConditionConfig condition = MqttConditionConfig.builder()
            .inject(identity())
            .session(new MqttSessionConfig("*"))
            .subscribe(new MqttSubscribeConfig("reply/one"))
            .subscribe(new MqttSubscribeConfig("reply/two"))
            .publish(new MqttPublishConfig("command/one"))
            .publish(new MqttPublishConfig("command/two"))
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"session\":[{\"client-id\":\"*\"}],\"subscribe\":[{\"topic\":\"reply/one\"}," +
            "{\"topic\":\"reply/two\"}],\"publish\":[{\"topic\":\"command/one\"},{\"topic\":\"command/two\"}]}"));
    }
}
