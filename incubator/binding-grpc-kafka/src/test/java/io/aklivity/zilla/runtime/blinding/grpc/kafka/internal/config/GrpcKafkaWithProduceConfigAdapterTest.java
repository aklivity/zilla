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

import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAnd;
import static com.vtence.hamcrest.jpa.HasFieldWithValue.hasField;
import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode.LEADER_ONLY;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithProduceConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithProduceConfigAdapter;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaWithProduceOverrideConfig;

public class GrpcKafkaWithProduceConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new GrpcKafkaWithProduceConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWith()
    {
        String text =
                "{" +
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

        GrpcKafkaWithProduceConfig with = jsonb.fromJson(text, GrpcKafkaWithProduceConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.topic, equalTo("items"));
        assertThat(with.acks, equalTo(LEADER_ONLY));
        assertThat(with.key.get(), equalTo("test"));
        assertThat(with.overrides,
            isPresentAnd(
                contains(
                    allOf(hasField("name", equalTo("header-test")),
                        hasField("value", equalTo("test"))))));
        assertThat(with.replyTo, equalTo("items-replies"));
    }

    @Test
    public void shouldWriteWith()
    {
        GrpcKafkaWithProduceConfig with = new GrpcKafkaWithProduceConfig(
            "items",
            LEADER_ONLY,
            "test",
            singletonList(new GrpcKafkaWithProduceOverrideConfig("header-test", "test")),
            "items-replies"
        );

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"topic\":\"items\",\"acks\":\"leader_only\",\"key\":\"test\"," +
            "\"overrides\":{\"header-test\":\"test\"},\"filters\":[{\"key\":\"fixed-key\"," +
            "\"headers\":{\"tag\":\"fixed-tag\"}}]}"));
    }
}
