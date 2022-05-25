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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAnd;
import static com.vtence.hamcrest.jpa.HasFieldWithValue.hasField;
import static io.aklivity.zilla.runtime.binding.sse.kafka.internal.config.SseKafkaWithConfig.EVENT_ID_DEFAULT;
import static io.aklivity.zilla.runtime.binding.sse.kafka.internal.config.SseKafkaWithConfig.EVENT_ID_KEY64_AND_ETAG;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class SseKafkaWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new SseKafkaWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWithTopic()
    {
        String text =
                "{" +
                    "\"topic\": \"test\"" +
                "}";

        SseKafkaWithConfig with = jsonb.fromJson(text, SseKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.topic, equalTo("test"));
        assertThat(with.filters, isEmpty());
        assertThat(with.eventId, sameInstance(EVENT_ID_DEFAULT));
    }

    @Test
    public void shouldWriteWithTopic()
    {
        SseKafkaWithConfig with = new SseKafkaWithConfig("test", null, EVENT_ID_DEFAULT);

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"topic\":\"test\"}"));
    }

    @Test
    public void shouldReadWithTopicAndFilters()
    {
        String text =
                "{" +
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

        SseKafkaWithConfig with = jsonb.fromJson(text, SseKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.topic, equalTo("test"));
        assertThat(with.filters.get(), contains(
                both(hasField("key", isPresentAnd(equalTo("fixed-key")))).
                and(hasField("headers", isPresentAnd(contains(
                    both(hasField("name", equalTo("tag"))).
                    and(hasField("value", equalTo("fixed-tag")))))))));
        assertThat(with.eventId, sameInstance(EVENT_ID_DEFAULT));
    }

    @Test
    public void shouldWriteWithTopicAndFilters()
    {
        SseKafkaWithConfig with = new SseKafkaWithConfig(
                "test",
                singletonList(new SseKafkaWithFilterConfig(
                    "fixed-key",
                    singletonList(new SseKafkaWithFilterHeaderConfig(
                        "tag",
                        "fixed-tag")))),
                EVENT_ID_DEFAULT);

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text,
                equalTo("{\"topic\":\"test\",\"filters\":[{\"key\":\"fixed-key\",\"headers\":{\"tag\":\"fixed-tag\"}}]}"));
        assertThat(with.eventId, sameInstance(EVENT_ID_DEFAULT));
    }

    @Test
    public void shouldReadWithTopicAndEventId()
    {
        String text =
                "{" +
                    "\"topic\": \"test\"," +
                    "\"event\":" +
                    "{" +
                        "\"id\": \"[\\\"${base64(key)}\\\",\\\"${etag}\\\"]\"" +
                    "}" +
                "}";

        SseKafkaWithConfig with = jsonb.fromJson(text, SseKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.topic, equalTo("test"));
        assertThat(with.filters, isEmpty());
        assertThat(with.eventId, sameInstance(EVENT_ID_KEY64_AND_ETAG));
    }

    @Test
    public void shouldWriteWithTopicAndEventId()
    {
        SseKafkaWithConfig with = new SseKafkaWithConfig(
                "test",
                null,
                "[\"${base64(key)}\",\"${etag}\"]");

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text,
                equalTo("{\"topic\":\"test\",\"event\":{\"id\":\"[\\\"${base64(key)}\\\",\\\"${etag}\\\"]\"}}"));
    }
}
