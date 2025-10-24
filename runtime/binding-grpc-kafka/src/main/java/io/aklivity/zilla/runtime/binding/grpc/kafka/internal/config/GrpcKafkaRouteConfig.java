/*
 * Copyright 2021-2024 Aklivity Inc
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

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.grpc.kafka.config.GrpcKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.config.GrpcKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;


public final class GrpcKafkaRouteConfig
{
    public final long id;

    private final List<GrpcKafkaConditionMatcher> when;
    public final GrpcKafkaWithResolver with;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public GrpcKafkaRouteConfig(
        GrpcKafkaOptionsConfig options,
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(GrpcKafkaConditionConfig.class::cast)
            .map(GrpcKafkaConditionMatcher::new)
            .collect(toList());

        final Map<String, LongFunction<String>> identifiers = route.guarded.stream()
            .collect(Collectors.toMap(g -> g.name, g -> g.identity));

        final LongFunction<String> defaultIdentifier = a -> null;
        final LongObjectBiFunction<MatchResult, String> identityReplacer = (a, r) ->
        {
            final LongFunction<String> identifier = identifiers.getOrDefault(r.group(1), defaultIdentifier);
            final String identity = identifier.apply(a);
            return identity != null ? identity : "";
        };

        final Map<String, LongObjectBiFunction<String, String>> attributors = route.guarded.stream()
            .collect(Collectors.toMap(g -> g.name, g -> g.attributes));

        final LongObjectBiFunction<String, String> defaultAttributor = (sessionId, name) -> null;
        final LongObjectBiFunction<MatchResult, String> attributeReplacer = (sessionId, match) ->
        {
            final LongObjectBiFunction<String, String> attributor =
                attributors.getOrDefault(match.group(1), defaultAttributor);

            final String value = attributor.apply(sessionId, match.group(2));
            return value != null ? value : "";
        };

        this.with = Optional.of(route.with)
            .map(GrpcKafkaWithConfig.class::cast)
            .map(c -> new GrpcKafkaWithResolver(options, identityReplacer, attributeReplacer, c))
            .get();
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        String16FW service,
        String16FW method,
        Array32FW<GrpcMetadataFW> metadata)
    {
        return when.isEmpty() || service != null && method != null &&
            when.stream().anyMatch(m -> m.matches(service, method, metadata));
    }
}
