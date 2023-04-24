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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;


public class KafkaGrpcWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new KafkaGrpcWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"scheme\":\"http\"," +
                    "\"authority\":\"localhost:8080\"," +
                    "\"entry\":\"grpc0\"" +
                "}";

        KafkaGrpcWithConfig with = jsonb.fromJson(text, KafkaGrpcWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.scheme.asString(), equalTo("http"));
        assertThat(with.authority.asString(), equalTo("localhost:8080"));
        assertThat(with.entry, equalTo("grpc0"));
    }

    @Test
    public void shouldWriteOptions()
    {
        KafkaGrpcWithConfig options = new KafkaGrpcWithConfig(
                new String16FW("http"),
                new String16FW("localhost:8080"),
                "grpc0");

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{" +
                    "\"scheme\":\"http\"," +
                    "\"authority\":\"localhost:8080\"," +
                    "\"entry\":\"grpc0\"" +
                "}"));
    }
}
