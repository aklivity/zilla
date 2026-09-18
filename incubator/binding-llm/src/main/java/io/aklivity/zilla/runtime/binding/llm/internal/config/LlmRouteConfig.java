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
package io.aklivity.zilla.runtime.binding.llm.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.config.binding.llm.LlmConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class LlmRouteConfig
{
    public final long id;
    public final int order;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;
    private final List<LlmConditionMatcher> matchers;

    public LlmRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.order = route.order;
        this.authorized = route.authorized;
        this.matchers = route.when.stream()
            .map(LlmConditionConfig.class::cast)
            .map(LlmConditionMatcher::new)
            .collect(toList());
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        String dialect,
        String model)
    {
        return matchers.isEmpty() || matchers.stream().anyMatch(m -> m.matches(dialect, model));
    }
}
