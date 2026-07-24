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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class GrpcKafkaWithFetchFilterConfigBuilder<T> extends
    ConfigBuilder<T, GrpcKafkaWithFetchFilterConfigBuilder<T>>
{
    private final Function<GrpcKafkaWithFetchFilterConfig, T> mapper;
    private String key;
    private List<GrpcKafkaWithFetchFilterHeaderConfig> headers;

    GrpcKafkaWithFetchFilterConfigBuilder(
        Function<GrpcKafkaWithFetchFilterConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaWithFetchFilterConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaWithFetchFilterConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaWithFetchFilterConfigBuilder<T> key(
        String key)
    {
        this.key = key;
        return this;
    }

    public GrpcKafkaWithFetchFilterConfigBuilder<T> headers(
        List<GrpcKafkaWithFetchFilterHeaderConfig> headers)
    {
        this.headers = headers;
        return this;
    }

    public GrpcKafkaWithFetchFilterConfigBuilder<T> header(
        String name,
        String value)
    {
        if (headers == null)
        {
            headers = new ArrayList<>();
        }

        headers.add(GrpcKafkaWithFetchFilterHeaderConfig.builder()
                        .name(name)
                        .value(value)
                        .build());

        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcKafkaWithFetchFilterConfig(key, headers));
    }
}
