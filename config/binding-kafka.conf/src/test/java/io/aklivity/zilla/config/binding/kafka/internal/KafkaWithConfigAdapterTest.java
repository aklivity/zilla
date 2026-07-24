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
package io.aklivity.zilla.config.binding.kafka.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.kafka.KafkaWithConfig;

public class KafkaWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new KafkaWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWith()
    {
        String text =
                "{" +
                    "\"defaultOffset\": \"historical\"," +
                    "\"deltaType\": \"json_patch\"," +
                    "\"acks\": \"leader_only\"" +
                "}";

        KafkaWithConfig with = jsonb.fromJson(text, KafkaWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.defaultOffset, equalTo("historical"));
        assertThat(with.deltaType, equalTo("json_patch"));
        assertThat(with.ackMode, equalTo("leader_only"));
    }

    @Test
    public void shouldWriteCondition()
    {
        KafkaWithConfig with = KafkaWithConfig.builder()
                .defaultOffset("historical")
                .deltaType("json_patch")
                .ackMode("leader_only")
                .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"defaultOffset\":\"historical\",\"deltaType\":\"json_patch\",\"acks\":\"leader_only\"}"));
    }
}
