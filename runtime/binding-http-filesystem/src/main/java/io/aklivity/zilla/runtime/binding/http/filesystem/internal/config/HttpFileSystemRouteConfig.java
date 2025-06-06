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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.binding.http.filesystem.config.HttpFileSystemConditionConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class HttpFileSystemRouteConfig
{
    public final long id;
    public final Optional<HttpFileSystemWithResolver> with;

    private final List<HttpFileSystemConditionMatcher> when;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public HttpFileSystemRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.with = Optional.ofNullable(route.with)
            .map(HttpFileSystemWithConfig.class::cast)
            .map(HttpFileSystemWithResolver::new);
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
