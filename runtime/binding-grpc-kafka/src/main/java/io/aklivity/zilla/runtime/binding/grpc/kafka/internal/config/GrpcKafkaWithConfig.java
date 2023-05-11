/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config;

import java.util.Optional;

import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class GrpcKafkaWithConfig extends WithConfig
{
    public final GrpcKafkaCapability capability;
    public final Optional<GrpcKafkaWithFetchConfig> fetch;
    public final Optional<GrpcKafkaWithProduceConfig> produce;

    public GrpcKafkaWithConfig(
        GrpcKafkaWithFetchConfig fetch)
    {
        this(GrpcKafkaCapability.FETCH, fetch, null);
    }

    public GrpcKafkaWithConfig(
        GrpcKafkaWithProduceConfig produce)
    {
        this(GrpcKafkaCapability.PRODUCE, null, produce);
    }

    private GrpcKafkaWithConfig(
        GrpcKafkaCapability capability,
        GrpcKafkaWithFetchConfig fetch,
        GrpcKafkaWithProduceConfig produce)
    {
        this.capability = capability;
        this.fetch = Optional.ofNullable(fetch);
        this.produce = Optional.ofNullable(produce);
    }
}
