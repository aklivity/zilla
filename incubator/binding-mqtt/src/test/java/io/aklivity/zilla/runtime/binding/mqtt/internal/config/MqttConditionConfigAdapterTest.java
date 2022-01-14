/*
 * Copyright 2021-2022 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttCapabilities.PUBLISH_ONLY;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttCapabilities.SUBSCRIBE_ONLY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

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
                    "\"topic\": \"test\"," +
                    "\"capabilities\": \"publish_only\"" +
                "}";

        MqttConditionConfig condition = jsonb.fromJson(text, MqttConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.topic, equalTo("test"));
        assertThat(condition.capabilities, equalTo(PUBLISH_ONLY));
    }

    @Test
    public void shouldWriteCondition()
    {
        MqttConditionConfig condition = new MqttConditionConfig("test", SUBSCRIBE_ONLY);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"topic\":\"test\",\"capabilities\":\"subscribe_only\"}"));
    }
}
