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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaConditionConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public final class SseKafkaRouteConfig extends OptionsConfig
{
    public final long id;
    public final Optional<SseKafkaWithResolver> with;

    private final List<SseKafkaConditionMatcher> when;
    private final LongPredicate authorized;

    public SseKafkaRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;

        final Map<String, LongFunction<String>> identifiers = route.guarded.stream()
                .collect(Collectors.toMap(g -> g.name, g -> g.identity));

        final LongFunction<String> defaultIdentifier = a -> null;
        final LongObjectBiFunction<MatchResult, String> identityReplacer = (a, r) ->
        {
            final LongFunction<String> identifier = identifiers.getOrDefault(r.group(1), defaultIdentifier);
            final String identity = identifier.apply(a);
            return identity != null ? identity : "";
        };

        this.with = Optional.ofNullable(route.with)
            .map(SseKafkaWithConfig.class::cast)
            .map(c -> new SseKafkaWithResolver(identityReplacer, c));

        Consumer<SseKafkaConditionMatcher> observer = with.isPresent() ? with.get()::onConditionMatched : null;
        this.when = route.when.stream()
                .map(SseKafkaConditionConfig.class::cast)
                .map(SseKafkaConditionMatcher::new)
                .peek(m -> m.observe(observer))
                .collect(toList());
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization);
    }

    boolean matches(
        String path)
    {
        return when.isEmpty() || path != null && when.stream().anyMatch(m -> m.matches(path));
    }
}
