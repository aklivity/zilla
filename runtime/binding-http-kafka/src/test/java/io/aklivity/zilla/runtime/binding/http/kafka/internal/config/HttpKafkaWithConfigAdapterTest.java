/*
 * Copyright 2021-2022 Aklivity Inc
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
        HttpKafkaWithConfig with = new HttpKafkaWithConfig(
                new HttpKafkaWithFetchConfig("test", null, null));

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
        HttpKafkaWithConfig with = new HttpKafkaWithConfig(
                new HttpKafkaWithFetchConfig(
                    "test",
                    singletonList(new HttpKafkaWithFetchFilterConfig(
                        "fixed-key",
                        singletonList(new HttpKafkaWithFetchFilterHeaderConfig(
                            "tag",
                            "fixed-tag")))),
                    null));

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
        HttpKafkaWithConfig with = new HttpKafkaWithConfig(
                new HttpKafkaWithFetchConfig(
                    "test",
                    null,
                    new HttpKafkaWithFetchMergeConfig("application/json", "{\"data\":[]}", "/data/-")));

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
        HttpKafkaWithConfig with = new HttpKafkaWithConfig(
                new HttpKafkaWithProduceConfig("test", null, null, null, null));

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"test\"}"));
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
        HttpKafkaWithConfig with = new HttpKafkaWithConfig(
                new HttpKafkaWithProduceConfig("test", "${params.id}", null, null, null));

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
        HttpKafkaWithConfig with = new HttpKafkaWithConfig(
                new HttpKafkaWithProduceConfig(
                        "test",
                        null,
                        singletonList(new HttpKafkaWithProduceOverrideConfig("id", "${params.id}")),
                        null,
                        null));

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
        HttpKafkaWithConfig with = new HttpKafkaWithConfig(
                new HttpKafkaWithProduceConfig("test", null, null, "replies", null));

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
        HttpKafkaWithConfig with = new HttpKafkaWithConfig(
                new HttpKafkaWithProduceConfig(
                        "test",
                        null,
                        null,
                        null,
                        singletonList(
                            new HttpKafkaWithProduceAsyncHeaderConfig("location", "/items/${params.id};${correlationId}"))));

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"test\"," +
                                  "\"async\":{\"location\":\"/items/${params.id};${correlationId}\"}}"));
    }
}
