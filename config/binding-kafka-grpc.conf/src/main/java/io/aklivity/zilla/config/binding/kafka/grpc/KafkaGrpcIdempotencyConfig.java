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

public final class KafkaGrpcIdempotencyConfig
{
    public final String metadata;

    public static KafkaGrpcIdempotencyConfigBuilder<KafkaGrpcIdempotencyConfig> builder()
    {
        return new KafkaGrpcIdempotencyConfigBuilder<>(KafkaGrpcIdempotencyConfig.class::cast);
    }

    public static <T> KafkaGrpcIdempotencyConfigBuilder<T> builder(
        Function<KafkaGrpcIdempotencyConfig, T> mapper)
    {
        return new KafkaGrpcIdempotencyConfigBuilder<>(mapper);
    }

    KafkaGrpcIdempotencyConfig(
        String metadata)
    {
        this.metadata = metadata;
    }
}
