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

import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaAckMode.LEADER_ONLY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.grpc.config.KafkaGrpcCorrelationConfig;
import io.aklivity.zilla.runtime.binding.kafka.grpc.config.KafkaGrpcIdempotencyConfig;
import io.aklivity.zilla.runtime.binding.kafka.grpc.config.KafkaGrpcOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class KafkaGrpcOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new KafkaGrpcOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String yaml =
                """
                acks: leader_only
                correlation:
                  headers:
                    service: zilla:x-service
                    method: zilla:x-method
                    correlation-id: zilla:x-correlation-id
                    reply-to: zilla:x-reply-to
                """;

        KafkaGrpcOptionsConfig options = jsonb.fromJson(yaml, KafkaGrpcOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.acks, equalTo(LEADER_ONLY));
        assertThat(options.correlation, not(nullValue()));
        assertThat(options.correlation.service, equalTo("zilla:x-service"));
        assertThat(options.correlation.method, equalTo("zilla:x-method"));
        assertThat(options.correlation.correlationId, equalTo("zilla:x-correlation-id"));
        assertThat(options.correlation.replyTo, equalTo("zilla:x-reply-to"));
    }

    @Test
    public void shouldWriteOptions()
    {
        KafkaGrpcOptionsConfig options = new KafkaGrpcOptionsConfig(
                LEADER_ONLY,
                new KafkaGrpcIdempotencyConfig("zilla:x-idempotency-key"),
                new KafkaGrpcCorrelationConfig(
                    "zilla:x-correlation-id",
                    "zilla:x-service",
                    "zilla:x-method",
                    "zilla:x-reply-to"));

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                acks: leader_only
                idempotency:
                  metadata: "zilla:x-idempotency-key"
                correlation:
                  headers:
                    service: "zilla:x-service"
                    method: "zilla:x-method"
                    correlation-id: "zilla:x-correlation-id"
                    reply-to: "zilla:x-reply-to"
                """));
    }
}
