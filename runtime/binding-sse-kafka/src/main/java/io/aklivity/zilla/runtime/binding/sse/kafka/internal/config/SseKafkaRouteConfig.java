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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;
import java.util.regex.MatchResult;

import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class SseKafkaRouteConfig
{
    public final long id;
    public final Optional<SseKafkaWithResolver> with;

    private final List<SseKafkaConditionMatcher> when;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public SseKafkaRouteConfig(
        RouteConfig route,
        EngineContext context)
    {
        this.id = route.id;

        // Build identifiers and attributors maps using EngineContext
        final Map<String, LongFunction<String>> identifiers = new HashMap<>();
        final Map<String, LongObjectBiFunction<String, String>> attributors = new HashMap<>();

        // Extract guard names referenced in expressions
        Set<String> referencedGuardNames = new HashSet<>();
        if (route.with != null)
        {
            SseKafkaWithConfig withConfig = (SseKafkaWithConfig) route.with;
            referencedGuardNames = SseKafkaWithResolver.extractGuardNames(withConfig);
        }

        // Only add guards that are actually referenced in expressions
        for (String guardName : referencedGuardNames)
        {
            // Try to find guard in route.guarded first
            route.guarded.stream()
                .filter(guarded -> guarded.name.equals(guardName))
                .findFirst()
                .ifPresentOrElse(
                    guarded ->
                    {
                        identifiers.put(guarded.name, guarded.identity);
                        attributors.put(guarded.name, guarded.attributes);
                    },
                    () ->
                    {
                        // Not found in route.guarded, resolve via EngineContext
                        long guardId = context.supplyTypeId(guardName);
                        GuardHandler guard = context.supplyGuard(guardId);

                        if (guard != null)
                        {
                            identifiers.put(guardName, guard::identity);
                            attributors.put(guardName, guard::attribute);
                        }
                    }
                );
        }

        final LongFunction<String> defaultIdentifier = a -> null;
        final LongObjectBiFunction<MatchResult, String> identityReplacer = (a, r) ->
        {
            final LongFunction<String> identifier = identifiers.getOrDefault(r.group(1), defaultIdentifier);
            final String identity = identifier.apply(a);
            return identity != null ? identity : "";
        };

        final LongObjectBiFunction<String, String> defaultAttributor = (sessionId, name) -> null;
        final LongObjectBiFunction<MatchResult, String> attributeReplacer = (sessionId, match) ->
        {
            final LongObjectBiFunction<String, String> attributor =
                attributors.getOrDefault(match.group(1), defaultAttributor);

            final String value = attributor.apply(sessionId, match.group(2));
            return value != null ? value : "";
        };

        this.with = Optional.ofNullable(route.with)
            .map(SseKafkaWithConfig.class::cast)
            .map(c -> new SseKafkaWithResolver(identityReplacer, attributeReplacer, c));

        Consumer<SseKafkaConditionMatcher> observer = with.isPresent() ? with.get()::onConditionMatched : null;
        this.when = route.when.stream()
                .map(SseKafkaConditionConfig.class::cast)
                .map(SseKafkaConditionMatcher::new)
                .peek(m -> m.observe(observer))
                .collect(toList());
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization,
        String path)
    {
        UnaryOperator<String> resolve = input ->
        {
            String format = input.replace("${method}", "%1$s").replace("${path}", "%2$s");
            return format != input
                ? format.formatted("GET", path)
                : input;
        };

        return authorized.test(authorization, resolve);
    }

    boolean matches(
        String path)
    {
        return when.isEmpty() || path != null && when.stream().anyMatch(m -> m.matches(path));
    }
}
