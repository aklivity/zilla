/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.sse.internal.config;

import static io.aklivity.zilla.runtime.engine.config.WithConfig.NO_COMPOSITE_ID;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.binding.sse.config.SseConditionConfig;
import io.aklivity.zilla.runtime.binding.sse.config.SseWithConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class SseRouteConfig
{
    public final long id;

    private final List<SseConditionMatcher> when;
    private final SseWithConfig with;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public SseRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(SseConditionConfig.class::cast)
            .map(SseConditionMatcher::new)
            .collect(toList());
        this.with = (SseWithConfig) route.with;
        this.authorized = route.authorized;
    }

    public long compositeId()
    {
        return with != null ? with.compositeId : NO_COMPOSITE_ID;
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
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(path));
    }
}
