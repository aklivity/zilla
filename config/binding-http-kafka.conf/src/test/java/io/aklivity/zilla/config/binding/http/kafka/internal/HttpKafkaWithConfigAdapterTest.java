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
package io.aklivity.zilla.config.binding.http.kafka.internal;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaCapability;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaWithConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaWithFetchConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaWithFetchFilterConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaWithProduceAsyncHeaderConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaWithProduceConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaWithProduceOverrideConfig;

public class HttpKafkaWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new HttpKafkaWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWithFetchTopic()
    {
        String text =
                "{" +
                    "\"capability\": \"fetch\"," +
                    "\"topic\": \"test\"" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.FETCH));
        assertThat(with.fetch.isPresent(), equalTo(true));
        assertThat(with.fetch.get().topic, equalTo("test"));
        assertThat(with.fetch.get().filters.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithFetchTopic()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .fetch(HttpKafkaWithFetchConfig.builder()
                .topic("test")
                .filters(null)
                .merged(null)
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"fetch\",\"topic\":\"test\"}"));
    }

    @Test
    public void shouldReadWithFetchTopicAndFilters()
    {
        String text =
                "{" +
                    "\"capability\": \"fetch\"," +
                    "\"topic\": \"test\"," +
                    "\"filters\":" +
                    "[" +
                        "{" +
                            "\"key\": \"fixed-key\"," +
                            "\"headers\":" +
                            "{" +
                                "\"tag\": \"fixed-tag\"" +
                            "}" +
                        "}" +
                    "]" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.FETCH));
        assertThat(with.fetch.isPresent(), equalTo(true));
        assertThat(with.fetch.get().topic, equalTo("test"));
        assertThat(with.fetch.get().filters.get().size(), equalTo(1));
        assertThat(with.fetch.get().filters.get().get(0).key.get(), equalTo("fixed-key"));
        assertThat(with.fetch.get().filters.get().get(0).headers.get().size(), equalTo(1));
        assertThat(with.fetch.get().filters.get().get(0).headers.get().get(0).name, equalTo("tag"));
        assertThat(with.fetch.get().filters.get().get(0).headers.get().get(0).value, equalTo("fixed-tag"));
        assertThat(with.produce.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithFetchTopicAndFilters()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .fetch(HttpKafkaWithFetchConfig.builder()
                .topic("test")
                .filters(singletonList(HttpKafkaWithFetchFilterConfig.builder()
                    .key("fixed-key")
                    .header("tag", "fixed-tag")
                    .build()))
                .merged(null)
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text,
                equalTo("{\"capability\":\"fetch\"," +
                         "\"topic\":\"test\"," +
                         "\"filters\":[{\"key\":\"fixed-key\",\"headers\":{\"tag\":\"fixed-tag\"}}]}"));
    }

    @Test
    public void shouldReadWithFetchTopicAndMerge()
    {
        String text =
                "{" +
                    "\"capability\": \"fetch\"," +
                    "\"topic\": \"test\"," +
                    "\"merge\":" +
                    "{" +
                        "\"content-type\": \"application/json\"," +
                        "\"patch\":" +
                        "{" +
                            "\"initial\": \"[]\"," +
                            "\"path\": \"/-\"" +
                        "}" +
                    "}" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.FETCH));
        assertThat(with.fetch.isPresent(), equalTo(true));
        assertThat(with.fetch.get().topic, equalTo("test"));
        assertThat(with.fetch.get().filters.isPresent(), equalTo(false));
        assertThat(with.fetch.get().merge.isPresent(), equalTo(true));
        assertThat(with.fetch.get().merge.get().contentType, equalTo("application/json"));
        assertThat(with.fetch.get().merge.get().initial, equalTo("[]"));
        assertThat(with.fetch.get().merge.get().path, equalTo("/-"));
        assertThat(with.produce.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithFetchTopicAndMerge()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .fetch(HttpKafkaWithFetchConfig.builder()
                .topic("test")
                .filters(null)
                .merged(HttpKafkaWithFetchMergeConfig.builder()
                    .contentType("application/json")
                    .initial("{\"data\":[]}")
                    .path("/data/-")
                    .build())
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text,
                equalTo("{\"capability\":\"fetch\"," +
                         "\"topic\":\"test\"," +
                         "\"merge\":{\"content-type\":\"application/json\"," +
                                    "\"patch\":{\"initial\":\"{\\\"data\\\":[]}\",\"path\":\"/data/-\"}}}"));
    }

    @Test
    public void shouldReadWithProduceTopic()
    {
        String text =
                "{" +
                    "\"capability\": \"produce\"," +
                    "\"topic\": \"test\"" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.PRODUCE));
        assertThat(with.fetch.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(true));
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key.isPresent(), equalTo(false));
        assertThat(with.produce.get().overrides.isPresent(), equalTo(false));
        assertThat(with.produce.get().replyTo.isPresent(), equalTo(false));
        assertThat(with.produce.get().async.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithProduceTopic()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .produce(HttpKafkaWithProduceConfig.builder()
                .topic("test")
                .acks("in_sync_replicas")
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"test\"}"));
    }

    @Test
    public void shouldReadWithProduceTopicAndAcks()
    {
        String text =
                "{" +
                    "\"capability\": \"produce\"," +
                    "\"topic\": \"test\"," +
                    "\"acks\": \"leader_only\"" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.PRODUCE));
        assertThat(with.fetch.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(true));
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().acks, equalTo("leader_only"));
        assertThat(with.produce.get().key.isPresent(), equalTo(false));
        assertThat(with.produce.get().overrides.isPresent(), equalTo(false));
        assertThat(with.produce.get().replyTo.isPresent(), equalTo(false));
        assertThat(with.produce.get().async.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithProduceTopicAndAcks()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .produce(HttpKafkaWithProduceConfig.builder()
                .topic("test")
                .acks("leader_only")
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"test\",\"acks\":\"leader_only\"}"));
    }

    @Test
    public void shouldReadWithProduceTopicAndKey()
    {
        String text =
                "{" +
                    "\"capability\": \"produce\"," +
                    "\"topic\": \"test\"," +
                    "\"key\": \"${params.id}\"" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.PRODUCE));
        assertThat(with.fetch.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(true));
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key.get(), equalTo("${params.id}"));
        assertThat(with.produce.get().overrides.isPresent(), equalTo(false));
        assertThat(with.produce.get().replyTo.isPresent(), equalTo(false));
        assertThat(with.produce.get().async.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithProduceTopicAndKey()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .produce(HttpKafkaWithProduceConfig.builder()
                .topic("test")
                .acks("in_sync_replicas")
                .key("${params.id}")
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"test\",\"key\":\"${params.id}\"}"));
    }

    @Test
    public void shouldReadWithProduceTopicAndOverrides()
    {
        String text =
                "{" +
                    "\"capability\": \"produce\"," +
                    "\"topic\": \"test\"," +
                    "\"overrides\":" +
                    "{" +
                        "\"id\":\"${params.id}\"" +
                    "}" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.PRODUCE));
        assertThat(with.fetch.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(true));
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key.isPresent(), equalTo(false));
        assertThat(with.produce.get().overrides.get().size(), equalTo(1));
        assertThat(with.produce.get().overrides.get().get(0).name, equalTo("id"));
        assertThat(with.produce.get().overrides.get().get(0).value, equalTo("${params.id}"));
        assertThat(with.produce.get().replyTo.isPresent(), equalTo(false));
        assertThat(with.produce.get().async.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithProduceTopicAndOverrides()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .produce(HttpKafkaWithProduceConfig.builder()
                .topic("test")
                .acks("in_sync_replicas")
                .overrides(singletonList(HttpKafkaWithProduceOverrideConfig.builder()
                    .name("id")
                    .value("${params.id}")
                    .build()))
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"test\",\"overrides\":{\"id\":\"${params.id}\"}}"));
    }

    @Test
    public void shouldReadWithProduceTopicAndReplyTo()
    {
        String text =
                "{" +
                    "\"capability\": \"produce\"," +
                    "\"topic\": \"test\"," +
                    "\"reply-to\": \"replies\"" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.PRODUCE));
        assertThat(with.fetch.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(true));
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key.isPresent(), equalTo(false));
        assertThat(with.produce.get().overrides.isPresent(), equalTo(false));
        assertThat(with.produce.get().replyTo.get(), equalTo("replies"));
        assertThat(with.produce.get().async.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithProduceTopicAndReplyTo()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .produce(HttpKafkaWithProduceConfig.builder()
                .topic("test")
                .acks("in_sync_replicas")
                .replyTo("replies")
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"test\",\"reply-to\":\"replies\"}"));
    }

    @Test
    public void shouldReadWithProduceTopicAndAsync()
    {
        String text =
                "{" +
                    "\"capability\": \"produce\"," +
                    "\"topic\": \"test\"," +
                    "\"async\":" +
                    "{" +
                        "\"location\":\"/items/${params.id};${correlationId}\"" +
                    "}" +
                "}";

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.PRODUCE));
        assertThat(with.fetch.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(true));
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key.isPresent(), equalTo(false));
        assertThat(with.produce.get().overrides.isPresent(), equalTo(false));
        assertThat(with.produce.get().replyTo.isPresent(), equalTo(false));
        assertThat(with.produce.get().async.get().size(), equalTo(1));
        assertThat(with.produce.get().async.get().get(0).name, equalTo("location"));
        assertThat(with.produce.get().async.get().get(0).value, equalTo("/items/${params.id};${correlationId}"));
    }

    @Test
    public void shouldWriteWithProduceTopicAndAsync()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .produce(HttpKafkaWithProduceConfig.builder()
                .topic("test")
                .acks("in_sync_replicas")
                .async(singletonList(
                            HttpKafkaWithProduceAsyncHeaderConfig.builder()
                                .name("location")
                                .value("/items/${params.id};${correlationId}")
                                .build()))
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"test\"," +
                                  "\"async\":{\"location\":\"/items/${params.id};${correlationId}\"}}"));
    }

    @Test
    public void shouldReadWithProduceTopicAndOverridesWithGuardIdentity()
    {
        String text = """
            {
                "capability": "produce",
                "topic": "test",
                "key": "${params.id}",
                "reply-to": "replies",
                "overrides":
                {
                    "zilla:identity": "${guarded['lsauthgaurd'].identity}"
                }
            }
            """;

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.PRODUCE));
        assertThat(with.fetch.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(true));
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key.get(), equalTo("${params.id}"));
        assertThat(with.produce.get().replyTo.get(), equalTo("replies"));
        assertThat(with.produce.get().overrides.get().size(), equalTo(1));
        assertThat(with.produce.get().overrides.get().get(0).name, equalTo("zilla:identity"));
        assertThat(with.produce.get().overrides.get().get(0).value, equalTo("${guarded['lsauthgaurd'].identity}"));
    }

    @Test
    public void shouldWriteWithProduceTopicAndOverridesWithGuardIdentity()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .produce(HttpKafkaWithProduceConfig.builder()
                .topic("test")
                .acks("in_sync_replicas")
                .key("${params.id}")
                .replyTo("replies")
                .overrides(singletonList(HttpKafkaWithProduceOverrideConfig.builder()
                    .name("zilla:identity")
                    .value("${guarded['lsauthgaurd'].identity}")
                    .build()))
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("""
            {\
            "capability":"produce",\
            "topic":"test",\
            "key":"${params.id}",\
            "overrides":{\
            "zilla:identity":"${guarded['lsauthgaurd'].identity}"\
            },\
            "reply-to":"replies"\
            }\
            """.trim()));
    }

    @Test
    public void shouldReadWithProduceTopicAndOverridesWithGuardAttributes()
    {
        String text = """
            {
                "capability": "produce",
                "topic": "test",
                "overrides":
                {
                    "zilla:identity": "${guarded['jwt'].identity}",
                    "zilla:username": "${guarded['jwt'].attributes.username}",
                    "zilla:email": "${guarded['jwt'].attributes.email}"
                }
            }
            """;

        HttpKafkaWithConfig with = jsonb.fromJson(text, HttpKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(HttpKafkaCapability.PRODUCE));
        assertThat(with.produce.get().overrides.get().size(), equalTo(3));
        assertThat(with.produce.get().overrides.get().get(0).name, equalTo("zilla:identity"));
        assertThat(with.produce.get().overrides.get().get(0).value, equalTo("${guarded['jwt'].identity}"));
        assertThat(with.produce.get().overrides.get().get(1).name, equalTo("zilla:username"));
        assertThat(with.produce.get().overrides.get().get(1).value, equalTo("${guarded['jwt'].attributes.username}"));
        assertThat(with.produce.get().overrides.get().get(2).name, equalTo("zilla:email"));
        assertThat(with.produce.get().overrides.get().get(2).value, equalTo("${guarded['jwt'].attributes.email}"));
    }

    @Test
    public void shouldWriteWithProduceTopicAndOverridesWithGuardAttributes()
    {
        HttpKafkaWithConfig with = HttpKafkaWithConfig.builder()
            .produce(HttpKafkaWithProduceConfig.builder()
                .topic("test")
                .acks("in_sync_replicas")
                .overrides(java.util.Arrays.asList(
                    HttpKafkaWithProduceOverrideConfig.builder()
                        .name("zilla:identity")
                        .value("${guarded['jwt'].identity}")
                        .build(),
                    HttpKafkaWithProduceOverrideConfig.builder()
                        .name("zilla:username")
                        .value("${guarded['jwt'].attributes.username}")
                        .build(),
                    HttpKafkaWithProduceOverrideConfig.builder()
                        .name("zilla:email")
                        .value("${guarded['jwt'].attributes.email}")
                        .build()))
                .build())
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("""
            {\
            "capability":"produce",\
            "topic":"test",\
            "overrides":{\
            "zilla:identity":"${guarded['jwt'].identity}",\
            "zilla:username":"${guarded['jwt'].attributes.username}",\
            "zilla:email":"${guarded['jwt'].attributes.email}"\
            }\
            }\
            """.trim()));
    }
}
