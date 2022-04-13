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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class HttpKafkaRouteConfig extends OptionsConfig
{
    public final long id;
    public final Optional<HttpKafkaWithResolver> with;

    private final List<HttpKafkaConditionMatcher> when;
    private final LongPredicate authorized;

    public HttpKafkaRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.with = Optional.ofNullable(route.with)
            .map(HttpKafkaWithConfig.class::cast)
            .map(HttpKafkaWithResolver::new);
        Consumer<HttpKafkaConditionMatcher> observer = with.isPresent() ? with.get()::onConditionMatched : null;
        this.when = route.when.stream()
                .map(HttpKafkaConditionConfig.class::cast)
                .map(HttpKafkaConditionMatcher::new)
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
        CharSequence method,
        CharSequence path)
    {
        return when.isEmpty() || method != null && path != null && when.stream().anyMatch(m -> m.matches(method, path));
    }
}
