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

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class KafkaGrpcOptionsConfigBuilder<T> extends ConfigBuilder<T, KafkaGrpcOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;
    private String acks;
    private KafkaGrpcIdempotencyConfig idempotency;
    private KafkaGrpcCorrelationConfig correlation;

    KafkaGrpcOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaGrpcOptionsConfigBuilder<T>> thisType()
    {
        return (Class<KafkaGrpcOptionsConfigBuilder<T>>) getClass();
    }

    public KafkaGrpcOptionsConfigBuilder<T> acks(
        String acks)
    {
        this.acks = acks;
        return this;
    }

    public KafkaGrpcOptionsConfigBuilder<T> idempotency(
        KafkaGrpcIdempotencyConfig idempotency)
    {
        this.idempotency = idempotency;
        return this;
    }

    public KafkaGrpcIdempotencyConfigBuilder<KafkaGrpcOptionsConfigBuilder<T>> idempotency()
    {
        return KafkaGrpcIdempotencyConfig.builder(this::idempotency);
    }

    public KafkaGrpcOptionsConfigBuilder<T> correlation(
        KafkaGrpcCorrelationConfig correlation)
    {
        this.correlation = correlation;
        return this;
    }

    public KafkaGrpcCorrelationConfigBuilder<KafkaGrpcOptionsConfigBuilder<T>> correlation()
    {
        return KafkaGrpcCorrelationConfig.builder(this::correlation);
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaGrpcOptionsConfig(acks, idempotency, correlation));
    }
}
