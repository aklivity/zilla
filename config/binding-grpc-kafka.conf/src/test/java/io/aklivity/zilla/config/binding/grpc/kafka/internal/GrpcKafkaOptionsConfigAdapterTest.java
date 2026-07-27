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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaCorrelationConfig;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaIdempotencyConfig;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaOptionsConfig;
import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaReliabilityConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class GrpcKafkaOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new GrpcKafkaOptionsConfigAdapter());
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
                reliability:
                  field: 255
                  metadata: last-message-id-x
                correlation:
                  headers:
                    service: zilla:service-x
                    method: zilla:method-x
                    correlation-id: zilla:correlation-id-x
                """;

        GrpcKafkaOptionsConfig options = jsonb.fromJson(yaml, GrpcKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.correlation, not(nullValue()));
        assertThat(options.reliability.field, equalTo(255));
        assertThat(options.reliability.metadata, equalTo("last-message-id-x"));
        assertThat(options.correlation.service, equalTo("zilla:service-x"));
        assertThat(options.correlation.method, equalTo("zilla:method-x"));
        assertThat(options.correlation.correlationId, equalTo("zilla:correlation-id-x"));
    }

    @Test
    public void shouldWriteOptions()
    {
        GrpcKafkaOptionsConfig options = GrpcKafkaOptionsConfig.builder()
                .reliability(GrpcKafkaReliabilityConfig.builder()
                    .field(255)
                    .metadata("x-last-message-id")
                    .build())
                .idempotency(GrpcKafkaIdempotencyConfig.builder()
                    .metadata("x-idempotency-key")
                    .build())
                .correlation(GrpcKafkaCorrelationConfig.builder()
                    .correlationId("zilla:x-correlation-id")
                    .service("zilla:x-service")
                    .method("zilla:x-method")
                    .replyTo("zilla:x-reply-to")
                    .build())
                .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                reliability:
                  field: 255
                  metadata: x-last-message-id
                idempotency:
                  metadata: x-idempotency-key
                correlation:
                  headers:
                    service: "zilla:x-service"
                    method: "zilla:x-method"
                    correlation-id: "zilla:x-correlation-id"
                    reply-to: "zilla:x-reply-to"
                """));
    }
}
