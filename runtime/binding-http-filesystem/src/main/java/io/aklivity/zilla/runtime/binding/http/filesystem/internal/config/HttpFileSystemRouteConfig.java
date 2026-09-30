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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;
import java.util.regex.MatchResult;

import io.aklivity.zilla.config.binding.http.filesystem.HttpFileSystemConditionConfig;
import io.aklivity.zilla.config.binding.http.filesystem.HttpFileSystemWithConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class HttpFileSystemRouteConfig
{
    public final long id;
    public final Optional<HttpFileSystemWithResolver> with;

    private final List<HttpFileSystemConditionMatcher> when;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public HttpFileSystemRouteConfig(
        RouteConfig route,
        EngineContext context)
    {
        this.id = route.id;

        final Map<String, LongFunction<String>> identifiers = new HashMap<>();
        final Map<String, LongObjectBiFunction<String, String>> attributors = new HashMap<>();

        Set<String> guardNames = Set.of();
        if (route.with != null)
        {
            HttpFileSystemWithConfig withConfig = (HttpFileSystemWithConfig) route.with;
            guardNames = HttpFileSystemWithResolver.extractGuardNames(withConfig);
        }

        for (String guardName : guardNames)
        {
            long guardId = route.resolveId.applyAsLong(guardName);
            GuardHandler guard = context.supplyGuard(guardId);

            if (guard != null)
            {
                identifiers.put(guardName, guard::identity);
                attributors.put(guardName, guard::attribute);
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

        this.with = Optional.ofNullable(route.with)
            .map(HttpFileSystemWithConfig.class::cast)
            .map(c -> new HttpFileSystemWithResolver(identityReplacer, attributeReplacer, c));
        Consumer<HttpFileSystemConditionMatcher> observer = with.isPresent() ? with.get()::onConditionMatched : null;
        this.when = route.when.stream()
                .map(HttpFileSystemConditionConfig.class::cast)
                .map(HttpFileSystemConditionMatcher::new)
                .peek(m -> m.observe(observer))
                .collect(toList());
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        String path,
        String method)
    {
        return when.isEmpty() || path != null && when.stream().anyMatch(m -> m.matches(path, method));
    }
}
