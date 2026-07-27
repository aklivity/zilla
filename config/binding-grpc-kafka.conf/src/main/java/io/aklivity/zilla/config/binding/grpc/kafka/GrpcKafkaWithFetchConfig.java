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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.WithConfig;

public final class GrpcKafkaWithFetchConfig extends WithConfig
{
    public final String topic;
    public final Optional<List<GrpcKafkaWithFetchFilterConfig>> filters;

    public static GrpcKafkaWithFetchConfigBuilder<GrpcKafkaWithFetchConfig> builder()
    {
        return new GrpcKafkaWithFetchConfigBuilder<>(GrpcKafkaWithFetchConfig.class::cast);
    }

    public static <T> GrpcKafkaWithFetchConfigBuilder<T> builder(
        Function<GrpcKafkaWithFetchConfig, T> mapper)
    {
        return new GrpcKafkaWithFetchConfigBuilder<>(mapper);
    }

    GrpcKafkaWithFetchConfig(
        String topic,
        List<GrpcKafkaWithFetchFilterConfig> filters)
    {
        this.topic = topic;
        this.filters = Optional.ofNullable(filters);
    }
}
