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
package io.aklivity.zilla.runtime.blinding.grpc.kafka.internal.config;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresent;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAnd;
import static com.vtence.hamcrest.jpa.HasFieldWithValue.hasField;
import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode.LEADER_ONLY;
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

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaCapability;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithConfigAdapter;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithFetchConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithFetchFilterConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithFetchFilterHeaderConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithProduceConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithProduceOverrideConfig;

public class GrpcKafkaWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new GrpcKafkaWithConfigAdapter());
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

        GrpcKafkaWithConfig with = jsonb.fromJson(text, GrpcKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(GrpcKafkaCapability.FETCH));
        assertThat(with.fetch, isPresent());
        assertThat(with.fetch.get().topic, equalTo("test"));
        assertThat(with.fetch.get().filters, isEmpty());
        assertThat(with.produce, isEmpty());
    }

    @Test
    public void shouldWriteWithFetchTopic()
    {
        GrpcKafkaWithConfig with = new GrpcKafkaWithConfig(
            new GrpcKafkaWithFetchConfig("test", null));

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

        GrpcKafkaWithConfig with = jsonb.fromJson(text, GrpcKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(GrpcKafkaCapability.FETCH));
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
        GrpcKafkaWithConfig with = new GrpcKafkaWithConfig(
            new GrpcKafkaWithFetchConfig(
                "test",
                singletonList(new GrpcKafkaWithFetchFilterConfig(
                    "fixed-key",
                    singletonList(new GrpcKafkaWithFetchFilterHeaderConfig(
                        "tag",
                        "fixed-tag"))))));

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text,
            equalTo("{\"capability\":\"fetch\"," +
                "\"topic\":\"test\"," +
                "\"filters\":[{\"key\":\"fixed-key\",\"headers\":{\"tag\":\"fixed-tag\"}}]}"));
    }

    @Test
    public void shouldReadWithProduce()
    {
        String text =
                "{" +
                "    \"capability\": \"produce\",\n" +
                "    \"topic\": \"items\",\n" +
                "    \"acks\": \"leader_only\",\n" +
                "    \"key\": \"test\",\n" +
                "    \"overrides\": {\n" +
                "        \"header-test\": \"test\"\n" +
                "    },\n" +
                "    \"reply-to\": \"items-replies\",\n" +
                "    \"filters\": [\n" +
                "        {\n" +
                "           \"key\": \"fixed-key\",\n" +
                "            \"headers\": {\n" +
                "                \"tag\": \"fixed-tag\"\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        GrpcKafkaWithConfig with = jsonb.fromJson(text, GrpcKafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.capability, equalTo(GrpcKafkaCapability.PRODUCE));
        assertThat(with.produce, isPresent());
        assertThat(with.produce.get().topic, equalTo("items"));
        assertThat(with.produce.get().acks, equalTo(LEADER_ONLY));
        assertThat(with.produce.get().key.get(), equalTo("test"));
        assertThat(with.produce.get().overrides,
            isPresentAnd(
                contains(
                    allOf(hasField("name", equalTo("header-test")),
                        hasField("value", equalTo("test"))))));
        assertThat(with.produce.get().replyTo, equalTo("items-replies"));
        assertThat(with.fetch, isEmpty());
    }

    @Test
    public void shouldWriteWithProduce()
    {
        GrpcKafkaWithConfig with = new GrpcKafkaWithConfig(
            new GrpcKafkaWithProduceConfig(
                "items",
                LEADER_ONLY,
                "test",
                singletonList(new GrpcKafkaWithProduceOverrideConfig("header-test", "test")),
                "items-replies"
            ));

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":\"produce\",\"topic\":\"items\"," +
            "\"acks\":\"leader_only\",\"key\":\"test\"," +
            "\"overrides\":{\"header-test\":\"test\"},\"reply-to\":\"items-replies\"}"));
    }
}
