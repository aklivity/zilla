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

import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;

public final class GrpcKafkaRouteConfigBuilder<T> extends RouteConfigBuilder<T, GrpcKafkaRouteConfigBuilder<T>>
{
    GrpcKafkaRouteConfigBuilder(
        Function<RouteConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaRouteConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaRouteConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaConditionConfigBuilder<GrpcKafkaRouteConfigBuilder<T>> when()
    {
        return new GrpcKafkaConditionConfigBuilder<>(this::when);
    }

    public GrpcKafkaWithConfigBuilder<GrpcKafkaRouteConfigBuilder<T>> with()
    {
        return new GrpcKafkaWithConfigBuilder<>(this::with);
    }
}
