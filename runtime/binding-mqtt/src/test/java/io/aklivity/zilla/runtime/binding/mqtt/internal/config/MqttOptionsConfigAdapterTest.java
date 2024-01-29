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

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttCredentialsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicConfig;
import io.aklivity.zilla.runtime.binding.mqtt.internal.config.MqttOptionsConfigAdapter.MqttVersion;
import io.aklivity.zilla.runtime.engine.test.internal.validator.config.TestValidatorConfig;

public class MqttOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new MqttOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"versions\":" +
                    "[" +
                        "v3.1.1," +
                        "v5" +
                    "]," +
                    "\"authorization\":" +
                    "{" +
                        "\"test0\":" +
                        "{" +
                            "\"credentials\":" +
                            "{" +
                                "\"connect\":" +
                                "{" +
                                    "\"username\":\"Bearer {credentials}\"" +
                                "}" +
                            "}" +
                        "}" +
                    "}," +
                    "\"topics\":" +
                    "[" +
                        "{" +
                            "\"name\": \"sensor/one\"," +
                            "\"content\":\"test\"" +
                        "}" +
                    "]" +
                "}";

        MqttOptionsConfig options = jsonb.fromJson(text, MqttOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.authorization.credentials, not(nullValue()));
        assertThat(options.authorization.credentials.connect, not(nullValue()));
        assertThat(options.authorization.credentials.connect, hasSize(1));
        assertThat(options.authorization.credentials.connect.get(0), not(nullValue()));
        assertThat(options.authorization.credentials.connect.get(0).property,
            equalTo(MqttPatternConfig.MqttConnectProperty.USERNAME));
        assertThat(options.authorization.credentials.connect.get(0).pattern,
            equalTo("Bearer {credentials}"));

        MqttTopicConfig topic = options.topics.get(0);
        assertThat(topic.name, equalTo("sensor/one"));
        assertThat(topic.content, instanceOf(TestValidatorConfig.class));
        assertThat(topic.content.type, equalTo("test"));

        MqttVersion version1 = options.versions.get(0);
        assertThat(version1.versionNumber(), equalTo(4));
        MqttVersion version2 = options.versions.get(1);
        assertThat(version2.versionNumber(), equalTo(5));
    }

    @Test
    public void shouldWriteOptions()
    {
        List<MqttTopicConfig> topics = new ArrayList<>();
        topics.add(new MqttTopicConfig("sensor/one", new TestValidatorConfig()));
        List<MqttVersion> versions = new ArrayList<>();
        versions.add(MqttVersion.VERSION_3_1_1);
        versions.add(MqttVersion.VERSION_5);

        MqttOptionsConfig options = new MqttOptionsConfig(
                new MqttAuthorizationConfig(
                    "test0",
                    new MqttCredentialsConfig(
                        singletonList(new MqttPatternConfig(
                            MqttPatternConfig.MqttConnectProperty.USERNAME,
                            "Bearer {credentials}")))),
                    topics, versions);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                    "{" +
                        "\"authorization\":" +
                        "{" +
                            "\"test0\":" +
                            "{" +
                                "\"credentials\":" +
                                "{" +
                                    "\"connect\":" +
                                    "{" +
                                        "\"username\":\"Bearer {credentials}\"" +
                                    "}" +
                                "}" +
                            "}" +
                        "}," +
                        "\"topics\":" +
                        "[" +
                            "{" +
                                "\"name\":\"sensor/one\"," +
                                "\"content\":\"test\"" +
                            "}" +
                        "]," +
                        "\"versions\":" +
                        "[" +
                            "\"v3.1.1\"," +
                            "\"v5\"" +
                        "]" +
                    "}"));
    }
}
