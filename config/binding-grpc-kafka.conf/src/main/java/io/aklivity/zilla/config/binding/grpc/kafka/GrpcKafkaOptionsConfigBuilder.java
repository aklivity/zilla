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

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class GrpcKafkaOptionsConfigBuilder<T> extends ConfigBuilder<T, GrpcKafkaOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;
    private GrpcKafkaReliabilityConfig reliability;
    private GrpcKafkaIdempotencyConfig idempotency;
    private GrpcKafkaCorrelationConfig correlation;

    GrpcKafkaOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaOptionsConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaOptionsConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaOptionsConfigBuilder<T> reliability(
        GrpcKafkaReliabilityConfig reliability)
    {
        this.reliability = reliability;
        return this;
    }

    public GrpcKafkaReliabilityConfigBuilder<GrpcKafkaOptionsConfigBuilder<T>> reliability()
    {
        return GrpcKafkaReliabilityConfig.builder(this::reliability);
    }

    public GrpcKafkaOptionsConfigBuilder<T> idempotency(
        GrpcKafkaIdempotencyConfig idempotency)
    {
        this.idempotency = idempotency;
        return this;
    }

    public GrpcKafkaIdempotencyConfigBuilder<GrpcKafkaOptionsConfigBuilder<T>> idempotency()
    {
        return GrpcKafkaIdempotencyConfig.builder(this::idempotency);
    }

    public GrpcKafkaOptionsConfigBuilder<T> correlation(
        GrpcKafkaCorrelationConfig correlation)
    {
        this.correlation = correlation;
        return this;
    }

    public GrpcKafkaCorrelationConfigBuilder<GrpcKafkaOptionsConfigBuilder<T>> correlation()
    {
        return GrpcKafkaCorrelationConfig.builder(this::correlation);
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcKafkaOptionsConfig(reliability, idempotency, correlation));
    }
}
