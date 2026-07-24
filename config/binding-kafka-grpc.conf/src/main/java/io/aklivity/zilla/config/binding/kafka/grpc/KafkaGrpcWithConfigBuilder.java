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
import io.aklivity.zilla.config.engine.WithConfig;

public final class KafkaGrpcWithConfigBuilder<T> extends ConfigBuilder<T, KafkaGrpcWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;
    private String scheme;
    private String authority;

    KafkaGrpcWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaGrpcWithConfigBuilder<T>> thisType()
    {
        return (Class<KafkaGrpcWithConfigBuilder<T>>) getClass();
    }

    public KafkaGrpcWithConfigBuilder<T> scheme(
        String scheme)
    {
        this.scheme = scheme;
        return this;
    }

    public KafkaGrpcWithConfigBuilder<T> authority(
        String authority)
    {
        this.authority = authority;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaGrpcWithConfig(scheme, authority));
    }
}
