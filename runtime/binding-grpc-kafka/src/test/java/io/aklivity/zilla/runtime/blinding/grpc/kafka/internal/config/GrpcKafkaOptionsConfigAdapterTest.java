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
package io.aklivity.zilla.runtime.blinding.grpc.kafka.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.grpc.kafka.config.GrpcKafkaCorrelationConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.config.GrpcKafkaIdempotencyConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.config.GrpcKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.config.GrpcKafkaReliabilityConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaOptionsConfigAdapter;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String8FW;

public class GrpcKafkaOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new GrpcKafkaOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"reliability\":" +
                    "{" +
                        "\"field\": 255," +
                        "\"metadata\": \"last-message-id-x\"" +
                    "}," +
                    "\"correlation\":" +
                    "{" +
                        "\"headers\":" +
                    "   {" +
                            "\"service\":\"zilla:service-x\"," +
                            "\"method\":\"zilla:method-x\"," +
                            "\"correlation-id\":\"zilla:correlation-id-x\"" +
                        "}" +
                    "}" +
                "}";

        GrpcKafkaOptionsConfig options = jsonb.fromJson(text, GrpcKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.correlation, not(nullValue()));
        assertThat(options.reliability.field, equalTo(255));
        assertThat(options.reliability.metadata.asString(), equalTo("last-message-id-x"));
        assertThat(options.correlation.service.asString(), equalTo("zilla:service-x"));
        assertThat(options.correlation.method.asString(), equalTo("zilla:method-x"));
        assertThat(options.correlation.correlationId.asString(), equalTo("zilla:correlation-id-x"));
    }

    @Test
    public void shouldWriteOptions()
    {
        GrpcKafkaOptionsConfig options = new GrpcKafkaOptionsConfig(
                new GrpcKafkaReliabilityConfig(255,
                    new String8FW("x-last-message-id")),
                new GrpcKafkaIdempotencyConfig(new String8FW("x-idempotency-key")),
                new GrpcKafkaCorrelationConfig(
                    new String16FW("zilla:x-correlation-id"),
                    new String16FW("zilla:x-service"),
                    new String16FW("zilla:x-method"),
                    new String16FW("zilla:x-reply-to")));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{" +
                    "\"reliability\":" +
                    "{" +
                        "\"field\":255," +
                        "\"metadata\":\"x-last-message-id\"" +
                    "}," +
                    "\"idempotency\":" +
                    "{" +
                        "\"metadata\":\"x-idempotency-key\"" +
                    "}," +
                    "\"correlation\":" +
                    "{" +
                        "\"headers\":" +
                        "{" +
                            "\"service\":\"zilla:x-service\"," +
                            "\"method\":\"zilla:x-method\"," +
                            "\"correlation-id\":\"zilla:x-correlation-id\"," +
                            "\"reply-to\":\"zilla:x-reply-to\"" +
                        "}" +
                    "}" +
                "}"));
    }
}
