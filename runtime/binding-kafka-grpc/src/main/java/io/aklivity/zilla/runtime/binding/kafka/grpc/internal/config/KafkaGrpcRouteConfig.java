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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongPredicate;

import io.aklivity.zilla.runtime.binding.kafka.grpc.config.KafkaGrpcConditionConfig;
import io.aklivity.zilla.runtime.binding.kafka.grpc.config.KafkaGrpcOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class KafkaGrpcRouteConfig
{
    public final long id;
    public final KafkaGrpcWithConfig with;
    public final List<KafkaGrpcConditionResolver> when;
    private final LongPredicate authorized;

    public KafkaGrpcRouteConfig(
        KafkaGrpcOptionsConfig options,
        RouteConfig route)
    {
        this.id = route.id;
        this.with = (KafkaGrpcWithConfig) route.with;
        this.when = route.when.stream()
            .map(KafkaGrpcConditionConfig.class::cast)
            .map(c -> new KafkaGrpcConditionResolver(options, c, with))
            .collect(toList());

        this.authorized = route.authorized;
    }
}
