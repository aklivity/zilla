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

public final class GrpcKafkaReliabilityConfigBuilder<T> extends ConfigBuilder<T, GrpcKafkaReliabilityConfigBuilder<T>>
{
    private final Function<GrpcKafkaReliabilityConfig, T> mapper;
    private int field;
    private String metadata;

    GrpcKafkaReliabilityConfigBuilder(
        Function<GrpcKafkaReliabilityConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaReliabilityConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaReliabilityConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaReliabilityConfigBuilder<T> field(
        int field)
    {
        this.field = field;
        return this;
    }

    public GrpcKafkaReliabilityConfigBuilder<T> metadata(
        String metadata)
    {
        this.metadata = metadata;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcKafkaReliabilityConfig(field, metadata));
    }
}
