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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class GrpcKafkaWithFetchConfigBuilder<T> extends ConfigBuilder<T, GrpcKafkaWithFetchConfigBuilder<T>>
{
    private final Function<GrpcKafkaWithFetchConfig, T> mapper;
    private String topic;
    private List<GrpcKafkaWithFetchFilterConfig> filters;

    GrpcKafkaWithFetchConfigBuilder(
        Function<GrpcKafkaWithFetchConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaWithFetchConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaWithFetchConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaWithFetchConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    public GrpcKafkaWithFetchConfigBuilder<T> filters(
        List<GrpcKafkaWithFetchFilterConfig> filters)
    {
        this.filters = filters;
        return this;
    }

    public GrpcKafkaWithFetchFilterConfigBuilder<GrpcKafkaWithFetchConfigBuilder<T>> filter()
    {
        return GrpcKafkaWithFetchFilterConfig.builder(this::filter);
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcKafkaWithFetchConfig(topic, filters));
    }

    private GrpcKafkaWithFetchConfigBuilder<T> filter(
        GrpcKafkaWithFetchFilterConfig filter)
    {
        if (filters == null)
        {
            filters = new LinkedList<>();
        }

        filters.add(filter);
        return this;
    }
}
