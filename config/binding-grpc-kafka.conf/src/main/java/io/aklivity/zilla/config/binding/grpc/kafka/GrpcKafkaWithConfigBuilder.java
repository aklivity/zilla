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
import io.aklivity.zilla.config.engine.WithConfig;

public final class GrpcKafkaWithConfigBuilder<T> extends ConfigBuilder<T, GrpcKafkaWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private GrpcKafkaWithFetchConfig fetch;
    private GrpcKafkaWithProduceConfig produce;

    GrpcKafkaWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaWithConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaWithConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaWithFetchConfigBuilder<GrpcKafkaWithConfigBuilder<T>> fetch()
    {
        return GrpcKafkaWithFetchConfig.builder(this::fetch);
    }

    public GrpcKafkaWithConfigBuilder<T> fetch(
        GrpcKafkaWithFetchConfig fetch)
    {
        this.fetch = fetch;
        return this;
    }

    public GrpcKafkaWithProduceConfigBuilder<GrpcKafkaWithConfigBuilder<T>> produce()
    {
        return GrpcKafkaWithProduceConfig.builder(this::produce);
    }

    public GrpcKafkaWithConfigBuilder<T> produce(
        GrpcKafkaWithProduceConfig produce)
    {
        this.produce = produce;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(fetch != null ? new GrpcKafkaWithConfig(fetch) : new GrpcKafkaWithConfig(produce));
    }
}
