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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public final class HttpKafkaRouteConfig
{
    public final long id;
    public final HttpKafkaWithResolver with;

    private final List<HttpKafkaConditionMatcher> when;
    private final LongPredicate authorized;

    public HttpKafkaRouteConfig(
        HttpKafkaOptionsConfig options,
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

        this.with = Optional.of(route.with)
            .map(HttpKafkaWithConfig.class::cast)
            .map(c -> new HttpKafkaWithResolver(options, identityReplacer, c))
            .get();
        this.when = route.when.stream()
                .map(HttpKafkaConditionConfig.class::cast)
                .map(HttpKafkaConditionMatcher::new)
                .peek(m -> m.observe(with::onConditionMatched))
                .collect(toList());

        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization);
    }

    boolean matches(
        CharSequence method,
        CharSequence path)
    {
        return when.isEmpty() || method != null && path != null && when.stream().anyMatch(m -> m.matches(method, path));
    }
}
