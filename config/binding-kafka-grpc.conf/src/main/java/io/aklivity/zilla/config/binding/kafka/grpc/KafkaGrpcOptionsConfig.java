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
package io.aklivity.zilla.config.binding.kafka.grpc;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class KafkaGrpcOptionsConfig extends OptionsConfig
{
    public static final KafkaGrpcOptionsConfig DEFAULT =
        new KafkaGrpcOptionsConfig(
            "in_sync_replicas",
            new KafkaGrpcIdempotencyConfig("idempotency-key"),
            new KafkaGrpcCorrelationConfig("zilla:correlation-id", "zilla:service", "zilla:method", "zilla:reply-to"));

    public final String acks;
    public final KafkaGrpcIdempotencyConfig idempotency;
    public final KafkaGrpcCorrelationConfig correlation;

    public static KafkaGrpcOptionsConfigBuilder<KafkaGrpcOptionsConfig> builder()
    {
        return new KafkaGrpcOptionsConfigBuilder<>(KafkaGrpcOptionsConfig.class::cast);
    }

    public static <T> KafkaGrpcOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new KafkaGrpcOptionsConfigBuilder<>(mapper);
    }

    KafkaGrpcOptionsConfig(
        String acks,
        KafkaGrpcIdempotencyConfig idempotency,
        KafkaGrpcCorrelationConfig correlation)
    {
        this.acks = acks;
        this.idempotency = idempotency;
        this.correlation = correlation;
    }
}
