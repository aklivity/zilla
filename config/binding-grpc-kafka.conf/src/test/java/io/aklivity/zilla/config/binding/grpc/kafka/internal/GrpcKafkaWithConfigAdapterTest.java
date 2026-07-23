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
package io.aklivity.zilla.config.binding.grpc.kafka.internal;

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

import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaCapability;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaWithConfig;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaWithFetchConfig;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaWithFetchFilterConfig;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaWithFetchFilterHeaderConfig;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaWithProduceConfig;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaWithProduceOverrideConfig;

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
        assertThat(with.fetch.isPresent(), equalTo(true));
        assertThat(with.fetch.get().topic, equalTo("test"));
        assertThat(with.fetch.get().filters.isPresent(), equalTo(false));
        assertThat(with.produce.isPresent(), equalTo(false));
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
        assertThat(with.produce.isPresent(), equalTo(true));
        assertThat(with.produce.get().topic, equalTo("items"));
        assertThat(with.produce.get().acks, equalTo("leader_only"));
        assertThat(with.produce.get().key.get(), equalTo("test"));
        assertThat(with.produce.get().overrides.isPresent(), equalTo(true));
        assertThat(with.produce.get().overrides.get().size(), equalTo(1));
        assertThat(with.produce.get().overrides.get().get(0).name, equalTo("header-test"));
        assertThat(with.produce.get().overrides.get().get(0).value, equalTo("test"));
        assertThat(with.produce.get().replyTo, equalTo("items-replies"));
        assertThat(with.fetch.isPresent(), equalTo(false));
    }

    @Test
    public void shouldWriteWithProduce()
    {
        GrpcKafkaWithConfig with = new GrpcKafkaWithConfig(
            new GrpcKafkaWithProduceConfig(
                "items",
                "leader_only",
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
