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
package io.aklivity.zilla.config.binding.grpc.kafka;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class GrpcKafkaOptionsConfig extends OptionsConfig
{
    public static final GrpcKafkaOptionsConfig DEFAULT =
        new GrpcKafkaOptionsConfig(
            new GrpcKafkaReliabilityConfig(32767, "last-message-id"),
            new GrpcKafkaIdempotencyConfig("idempotency-key"),
            new GrpcKafkaCorrelationConfig("zilla:correlation-id", "zilla:service", "zilla:method", "zilla:reply-to"));

    public final GrpcKafkaReliabilityConfig reliability;
    public final GrpcKafkaIdempotencyConfig idempotency;
    public final GrpcKafkaCorrelationConfig correlation;

    public static GrpcKafkaOptionsConfigBuilder<GrpcKafkaOptionsConfig> builder()
    {
        return new GrpcKafkaOptionsConfigBuilder<>(GrpcKafkaOptionsConfig.class::cast);
    }

    public static <T> GrpcKafkaOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new GrpcKafkaOptionsConfigBuilder<>(mapper);
    }

    GrpcKafkaOptionsConfig(
        GrpcKafkaReliabilityConfig reliability,
        GrpcKafkaIdempotencyConfig idempotency,
        GrpcKafkaCorrelationConfig correlation)
    {
        this.reliability = reliability;
        this.idempotency = idempotency;
        this.correlation = correlation;
    }
}
