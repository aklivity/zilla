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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType.JSON_PATCH;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.LIVE;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class KafkaOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new KafkaOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"bootstrap\":" +
                    "[" +
                        "\"test\"" +
                    "]," +
                    "\"topics\":" +
                    "[" +
                        "{" +
                            "\"name\": \"test\"," +
                            "\"defaultOffset\": \"live\"," +
                            "\"deltaType\": \"json_patch\"" +
                        "}" +
                    "]," +
                    "\"sasl\":" +
                    "{" +
                        "\"mechanism\": \"plain\"," +
                        "\"username\": \"username\"," +
                        "\"password\": \"password\"" +
                    "}" +
                "}";

        KafkaOptionsConfig options = jsonb.fromJson(text, KafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.bootstrap, equalTo(singletonList("test")));
        assertThat(options.topics, equalTo(singletonList(KafkaTopicConfig.builder()
            .name("test").defaultOffset(LIVE).deltaType(JSON_PATCH).build())));
        assertThat(options.sasl.mechanism, equalTo("plain"));
        assertThat(options.sasl.username, equalTo("username"));
        assertThat(options.sasl.password, equalTo("password"));
    }

    @Test
    public void shouldWriteOptions()
    {
        KafkaOptionsConfig options = KafkaOptionsConfig.builder()
            .bootstrap("test")
            .topic()
                .name("test")
                .defaultOffset(LIVE)
                .deltaType(JSON_PATCH)
                .value(TestModelConfig.builder().build())
                .build()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .sasl()
                .mechanism("plain")
                .username("username")
                .password("password")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"bootstrap\":[\"test\"]," +
                "\"topics\":[{\"name\":\"test\",\"defaultOffset\":\"live\",\"deltaType\":\"json_patch\"," +
                "\"value\":\"test\"}]," +
                "\"servers\":[\"localhost:9092\"]," +
                "\"sasl\":{\"mechanism\":\"plain\",\"username\":\"username\",\"password\":\"password\"}}"));
    }

    @Test
    public void shouldReadSaslScramOptions()
    {
        String text =
                "{" +
                        "\"bootstrap\":" +
                        "[" +
                        "\"test\"" +
                        "]," +
                        "\"topics\":" +
                        "[" +
                        "{" +
                        "\"name\": \"test\"," +
                        "\"defaultOffset\": \"live\"," +
                        "\"deltaType\": \"json_patch\"" +
                        "}" +
                        "]," +
                        "\"sasl\":" +
                        "{" +
                        "\"mechanism\": \"scram-sha-256\"," +
                        "\"username\": \"username\"," +
                        "\"password\": \"password\"" +
                        "}" +
                        "}";

        KafkaOptionsConfig options = jsonb.fromJson(text, KafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.bootstrap, equalTo(singletonList("test")));
        assertThat(options.topics, equalTo(singletonList(
            KafkaTopicConfig.builder().name("test").defaultOffset(LIVE).deltaType(JSON_PATCH).build())));
        assertThat(options.sasl.mechanism, equalTo("scram-sha-256"));
        assertThat(options.sasl.username, equalTo("username"));
        assertThat(options.sasl.password, equalTo("password"));
    }

    @Test
    public void shouldWriteSaslScramOptions()
    {
        KafkaOptionsConfig options = KafkaOptionsConfig.builder()
            .bootstrap("test")
            .topic()
                .name("test")
                .defaultOffset(LIVE)
                .deltaType(JSON_PATCH)
                .build()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .sasl()
                .mechanism("scram-sha-256")
                .username("username")
                .password("password")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"bootstrap\":[\"test\"]," +
                "\"topics\":[{\"name\":\"test\",\"defaultOffset\":\"live\",\"deltaType\":\"json_patch\"}]," +
                "\"servers\":[\"localhost:9092\"]," +
                "\"sasl\":{\"mechanism\":\"scram-sha-256\",\"username\":\"username\",\"password\":\"password\"}}"));
    }

    @Test
    public void shouldWriteCatalogOptions()
    {
        KafkaOptionsConfig options = KafkaOptionsConfig.builder()
            .bootstrap("test")
            .topic()
                .name("test")
                .defaultOffset(LIVE)
                .deltaType(JSON_PATCH)
                .value(TestModelConfig.builder().length(0).build())
                .build()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .sasl()
                .mechanism("plain")
                .username("username")
                .password("password")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"bootstrap\":[\"test\"]," +
                "\"topics\":[{\"name\":\"test\",\"defaultOffset\":\"live\",\"deltaType\":\"json_patch\"," +
                "\"value\":\"test\"}]," +
                "\"servers\":[\"localhost:9092\"]," +
                "\"sasl\":{\"mechanism\":\"plain\",\"username\":\"username\",\"password\":\"password\"}}"));
    }

    @Test
    public void shouldReadHeadersOptions()
    {
        String text =
            "{" +
                "\"bootstrap\":" +
                "[" +
                    "\"test\"" +
                "]," +
                "\"topics\":" +
                "[" +
                    "{" +
                    "\"name\": \"test\"," +
                    "\"transforms\":" +
                    "[" +
                        "\"extract-headers\":" +
                        "{" +
                            "\"correlation-id\": \"${message.value.correlationId}\"" +
                        "}" +
                    "]" +
                    "}" +
                "]" +
            "}";

        KafkaOptionsConfig options = jsonb.fromJson(text, KafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.bootstrap, equalTo(singletonList("test")));
        assertEquals(options.topics.get(0).transforms.extractHeaders.get(0).name, "correlation-id");
        assertEquals(options.topics.get(0).transforms.extractHeaders.get(0).path, "$.correlationId");
    }

    @Test
    public void shouldWriteHeadersOptions()
    {
        KafkaOptionsConfig options = KafkaOptionsConfig.builder()
            .bootstrap("test")
            .topic()
                .name("test")
                .transforms()
                    .extractHeader("correlation-id", "${message.value.correlationId}")
                    .build()
                .value(TestModelConfig.builder().length(0).build())
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"bootstrap\":[\"test\"],\"topics\":[{\"name\":\"test\",\"value\":\"test\"," +
            "\"transforms\":{\"extract-headers\":{\"correlation-id\":\"$.correlationId\"}}}]}"));
    }
}
