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
package io.aklivity.zilla.runtime.binding.agui.internal.config;

import static java.util.function.UnaryOperator.identity;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.config.binding.agui.AguiConditionConfig;
import io.aklivity.zilla.config.binding.agui.AguiWithConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class AguiRouteConfig
{
    public final long id;

    private final List<AguiConditionMatcher> when;
    private final AguiWithResolver with;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public AguiRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.with = Optional.ofNullable(route.with)
            .map(AguiWithConfig.class::cast)
            .map(AguiWithResolver::new)
            .orElse(null);
        this.when = route.when.stream()
            .map(AguiConditionConfig.class::cast)
            .map(AguiConditionMatcher::new)
            .peek(m -> Optional.ofNullable(with).ifPresent(w -> m.observe(w::onConditionMatched)))
            .toList();
        this.authorized = route.authorized;
    }

    public boolean matches(
        CharSequence path)
    {
        return when.isEmpty() || path != null && when.stream().anyMatch(m -> m.matches(path));
    }

    public String agent()
    {
        return with != null ? with.resolveAgent() : null;
    }

    public boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }
}
