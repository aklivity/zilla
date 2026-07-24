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

public final class GrpcKafkaMetadataValueConfigBuilder<T> extends ConfigBuilder<T, GrpcKafkaMetadataValueConfigBuilder<T>>
{
    private final Function<GrpcKafkaMetadataValueConfig, T> mapper;
    private String textValue;
    private String base64Value;

    GrpcKafkaMetadataValueConfigBuilder(
        Function<GrpcKafkaMetadataValueConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaMetadataValueConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaMetadataValueConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaMetadataValueConfigBuilder<T> textValue(
        String textValue)
    {
        this.textValue = textValue;
        return this;
    }

    public GrpcKafkaMetadataValueConfigBuilder<T> base64Value(
        String base64Value)
    {
        this.base64Value = base64Value;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcKafkaMetadataValueConfig(textValue, base64Value));
    }
}
