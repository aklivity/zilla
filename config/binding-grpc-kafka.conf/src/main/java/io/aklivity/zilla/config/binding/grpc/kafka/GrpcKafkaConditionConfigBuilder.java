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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class GrpcKafkaConditionConfigBuilder<T> extends ConfigBuilder<T, GrpcKafkaConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;
    private String service;
    private String method;
    private Map<String, GrpcKafkaMetadataValueConfig> metadata;

    GrpcKafkaConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaConditionConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaConditionConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaConditionConfigBuilder<T> service(
        String service)
    {
        this.service = service;
        return this;
    }

    public GrpcKafkaConditionConfigBuilder<T> method(
        String method)
    {
        this.method = method;
        return this;
    }

    public GrpcKafkaConditionConfigBuilder<T> metadata(
        Map<String, GrpcKafkaMetadataValueConfig> metadata)
    {
        this.metadata = metadata;
        return this;
    }

    public GrpcKafkaConditionConfigBuilder<T> metadata(
        String name,
        GrpcKafkaMetadataValueConfig value)
    {
        if (metadata == null)
        {
            metadata = new LinkedHashMap<>();
        }

        metadata.put(name, value);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcKafkaConditionConfig(service, method, metadata));
    }
}
