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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresent;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAnd;
import static com.vtence.hamcrest.jpa.HasFieldWithValue.hasField;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceAsyncHeaderConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceOverrideConfig;

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
        assertThat(with.fetch, isPresent());
        assertThat(with.fetch.get().topic, equalTo("test"));
        assertThat(with.fetch.get().filters, isEmpty());
        assertThat(with.produce, isEmpty());
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
        assertThat(with.fetch, isPresent());
        assertThat(with.fetch.get().topic, equalTo("test"));
        assertThat(with.fetch.get().filters.get(), contains(
                both(hasField("key", isPresentAnd(equalTo("fixed-key")))).
                and(hasField("headers", isPresentAnd(contains(
                    both(hasField("name", equalTo("tag"))).
                    and(hasField("value", equalTo("fixed-tag")))))))));
        assertThat(with.produce, isEmpty());
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
        assertThat(with.fetch, isPresent());
        assertThat(with.fetch.get().topic, equalTo("test"));
        assertThat(with.fetch.get().filters, isEmpty());
        assertThat(with.fetch.get().merge, isPresentAnd(
                allOf(hasField("contentType", equalTo("application/json")),
                      hasField("initial", equalTo("[]")),
                      hasField("path", equalTo("/-")))));
        assertThat(with.produce, isEmpty());
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
        assertThat(with.fetch, isEmpty());
        assertThat(with.produce, isPresent());
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key, isEmpty());
        assertThat(with.produce.get().overrides, isEmpty());
        assertThat(with.produce.get().replyTo, isEmpty());
        assertThat(with.produce.get().async, isEmpty());
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
        assertThat(with.fetch, isEmpty());
        assertThat(with.produce, isPresent());
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().acks, equalTo(LEADER_ONLY));
        assertThat(with.produce.get().key, not(isPresent()));
        assertThat(with.produce.get().overrides, isEmpty());
        assertThat(with.produce.get().replyTo, isEmpty());
        assertThat(with.produce.get().async, isEmpty());
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
        assertThat(with.fetch, isEmpty());
        assertThat(with.produce, isPresent());
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key, isPresentAnd(equalTo("${params.id}")));
        assertThat(with.produce.get().overrides, isEmpty());
        assertThat(with.produce.get().replyTo, isEmpty());
        assertThat(with.produce.get().async, isEmpty());
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
        assertThat(with.fetch, isEmpty());
        assertThat(with.produce, isPresent());
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key, isEmpty());
        assertThat(with.produce.get().overrides,
                isPresentAnd(
                    contains(
                        allOf(hasField("name", equalTo("id")),
                              hasField("value", equalTo("${params.id}"))))));
        assertThat(with.produce.get().replyTo, isEmpty());
        assertThat(with.produce.get().async, isEmpty());
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
        assertThat(with.fetch, isEmpty());
        assertThat(with.produce, isPresent());
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key, isEmpty());
        assertThat(with.produce.get().overrides, isEmpty());
        assertThat(with.produce.get().replyTo, isPresentAnd(equalTo("replies")));
        assertThat(with.produce.get().async, isEmpty());
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
        assertThat(with.fetch, isEmpty());
        assertThat(with.produce, isPresent());
        assertThat(with.produce.get().topic, equalTo("test"));
        assertThat(with.produce.get().key, isEmpty());
        assertThat(with.produce.get().overrides, isEmpty());
        assertThat(with.produce.get().replyTo, isEmpty());
        assertThat(with.produce.get().async,
                isPresentAnd(
                    contains(
                        allOf(hasField("name", equalTo("location")),
                              hasField("value", equalTo("/items/${params.id};${correlationId}"))))));
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
}
