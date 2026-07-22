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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.grpc.config.KafkaGrpcConditionConfig;

public class KafkaGrpcConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new KafkaGrpcConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{\n" +
                "    \"topic\": \"responses\",\n" +
                "    \"reply-to\": \"requests\",\n" +
                "    \"key\": \"test\",\n" +
                "    \"headers\": {\n" +
                "        \"custom\": \"test\"\n" +
                "    },\n" +
                "    \"method\": \"test/*\"\n" +
                "}";

        KafkaGrpcConditionConfig condition = jsonb.fromJson(text, KafkaGrpcConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.topic, equalTo("responses"));
        assertThat(condition.replyTo.get(), equalTo("requests"));
        assertThat(condition.service.get(), equalTo("test"));
        assertThat(condition.method.get(), equalTo("*"));
        assertThat(condition.key.get(), equalTo("test"));
        assertTrue(!condition.headers.isEmpty());
    }

    @Test
    public void shouldWriteCondition()
    {
        KafkaGrpcConditionConfig condition = new KafkaGrpcConditionConfig(
            "responses",
            "requests",
            "test",
            singletonMap("custom", "test"),
            "test",
            "*"
        );

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"topic\":\"responses\",\"reply-to\":\"requests\",\"key\":\"test\"," +
            "\"headers\":{\"custom\":\"test\"},\"method\":\"test/*\"}"));
    }
}
