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
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String8FW;

public class KafkaGrpcOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new KafkaGrpcOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"acks\":\"leader_only\"," +
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
                "}";

        KafkaGrpcOptionsConfig options = jsonb.fromJson(text, KafkaGrpcOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.acks, equalTo(LEADER_ONLY));
        assertThat(options.correlation, not(nullValue()));
        assertThat(options.correlation.service.asString(), equalTo("zilla:x-service"));
        assertThat(options.correlation.method.asString(), equalTo("zilla:x-method"));
        assertThat(options.correlation.correlationId.asString(), equalTo("zilla:x-correlation-id"));
        assertThat(options.correlation.replyTo.asString(), equalTo("zilla:x-reply-to"));
    }

    @Test
    public void shouldWriteOptions()
    {
        KafkaGrpcOptionsConfig options = new KafkaGrpcOptionsConfig(
                LEADER_ONLY,
                new KafkaGrpcIdempotencyConfig(new String8FW("zilla:x-idempotency-key")),
                new KafkaGrpcCorrelationConfig(
                    new String16FW("zilla:x-correlation-id"),
                    new String16FW("zilla:x-service"),
                    new String16FW("zilla:x-method"),
                    new String16FW("zilla:x-reply-to")));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{" +
                    "\"acks\":\"leader_only\"," +
                    "\"idempotency\":" +
                    "{" +
                        "\"metadata\":\"zilla:x-idempotency-key\"" +
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
