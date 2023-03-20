/*
 * Copyright 2021-2022 Aklivity Inc
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

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

import java.util.List;
import java.util.function.LongPredicate;

import static java.util.stream.Collectors.toList;

public final class GrpcKafkaRouteConfig extends OptionsConfig
{
    public final long id;

    private final List<GrpcKafkaConditionMatcher> when;
    private final LongPredicate authorized;

    public GrpcKafkaRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(GrpcKafkaConditionConfig.class::cast)
            .map(GrpcKafkaConditionMatcher::new)
            .collect(toList());
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization);
    }

    boolean matches(
        String16FW service,
        String16FW method,
        Array32FW<GrpcMetadataFW> metadataHeaders)
    {
        return when.isEmpty() || service != null && method != null &&
            when.stream().anyMatch(m -> m.matches(service, method, metadataHeaders));
    }
}
