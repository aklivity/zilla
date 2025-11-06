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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class HttpKafkaRouteConfig
{
    public final long id;
    public final HttpKafkaWithResolver with;

    private final List<HttpKafkaConditionMatcher> when;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public HttpKafkaRouteConfig(
        HttpKafkaOptionsConfig options,
        RouteConfig route,
        EngineContext context)
    {
        this.id = route.id;

        // Build identifiers and attributors maps using EngineContext
        final Map<String, LongFunction<String>> identifiers = new HashMap<>();
        final Map<String, LongObjectBiFunction<String, String>> attributors = new HashMap<>();

        // First, add explicitly guarded routes (from route.guarded)
        for (var guarded : route.guarded)
        {
            identifiers.put(guarded.name, guarded.identity);
            attributors.put(guarded.name, guarded.attributes);
        }

        // Second, extract guard names referenced in expressions and resolve them via EngineContext
        if (route.with != null)
        {
            HttpKafkaWithConfig withConfig = (HttpKafkaWithConfig) route.with;
            Set<String> referencedGuardNames = extractReferencedGuards(withConfig);

            for (String guardName : referencedGuardNames)
            {
                // Skip if already added from route.guarded
                if (!identifiers.containsKey(guardName))
                {
                    try
                    {
                        // Resolve guard using EngineContext
                        long guardId = context.supplyTypeId(guardName);
                        GuardHandler guard = context.supplyGuard(guardId);

                        if (guard != null)
                        {
                            // Create identity and attribute functions that delegate to GuardHandler
                            identifiers.put(guardName, guard::identity);
                            attributors.put(guardName, guard::attribute);
                        }
                    }
                    catch (Exception ex)
                    {
                        // Guard not found or invalid - ignore and continue
                    }
                }
            }
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

        this.with = Optional.of(route.with)
            .map(HttpKafkaWithConfig.class::cast)
            .map(c -> new HttpKafkaWithResolver(options, identityReplacer, attributeReplacer, c))
            .get();
        this.when = route.when.stream()
                .map(HttpKafkaConditionConfig.class::cast)
                .map(HttpKafkaConditionMatcher::new)
                .peek(m -> m.observe(with::onConditionMatched))
                .collect(toList());

        this.authorized = route.authorized;
    }

    private Set<String> extractReferencedGuards(
        HttpKafkaWithConfig withConfig)
    {
        Set<String> guardNames = new HashSet<>();

        // Regex pattern matches ${guarded['<name>'].identity} and ${guarded['<name>'].attributes.*}
        // Capture group (1) extracts the guard name
        Pattern guardPattern = Pattern.compile("\\$\\{guarded\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]");

        // Extract from produce overrides
        withConfig.produce.ifPresent(produce ->
        {
            produce.overrides.ifPresent(overrides ->
            {
                for (var override : overrides)
                {
                    Matcher matcher = guardPattern.matcher(override.value);
                    while (matcher.find())
                    {
                        guardNames.add(matcher.group(1));
                    }
                }
            });
        });

        // Extract from fetch filters
        withConfig.fetch.ifPresent(fetch ->
        {
            fetch.filters.ifPresent(filters ->
            {
                for (var filter : filters)
                {
                    filter.headers.ifPresent(headers ->
                    {
                        for (var header : headers)
                        {
                            Matcher matcher = guardPattern.matcher(header.value);
                            while (matcher.find())
                            {
                                guardNames.add(matcher.group(1));
                            }
                        }
                    });
                }
            });
        });

        return guardNames;
    }

    boolean authorized(
        long authorization,
        CharSequence method,
        CharSequence path)
    {
        UnaryOperator<String> resolve = input ->
        {
            String format = input.replace("${method}", "%1$s").replace("${path}", "%2$s");
            return format != input
                ? format.formatted(method, path)
                : input;
        };

        return authorized.test(authorization, resolve);
    }

    boolean matches(
        CharSequence method,
        CharSequence path)
    {
        return when.isEmpty() || method != null && path != null && when.stream().anyMatch(m -> m.matches(method, path));
    }
}
