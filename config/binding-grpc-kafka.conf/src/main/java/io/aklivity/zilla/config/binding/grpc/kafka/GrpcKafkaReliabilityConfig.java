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

public final class GrpcKafkaReliabilityConfig
{
    public final int field;
    public final String metadata;

    public static GrpcKafkaReliabilityConfigBuilder<GrpcKafkaReliabilityConfig> builder()
    {
        return new GrpcKafkaReliabilityConfigBuilder<>(GrpcKafkaReliabilityConfig.class::cast);
    }

    public static <T> GrpcKafkaReliabilityConfigBuilder<T> builder(
        Function<GrpcKafkaReliabilityConfig, T> mapper)
    {
        return new GrpcKafkaReliabilityConfigBuilder<>(mapper);
    }

    GrpcKafkaReliabilityConfig(
        int field,
        String metadata)
    {
        this.field = field;
        this.metadata = metadata;
    }
}
