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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.config;

import static java.util.function.UnaryOperator.identity;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aConditionConfig;
import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aWithConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class AguiA2aRouteConfig
{
    private static final Pattern IDENTITY_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\])\\.identity\\}");

    public final long id;
    public final String agent;
    public final AguiA2aWithConfig with;
    public final List<GuardedConfig> guarded;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;
    private final Matcher identityMatcher;
    private final LongFunction<GuardHandler> supplyGuard;
    private final RouteConfig route;

    public AguiA2aRouteConfig(
        RouteConfig route,
        LongFunction<GuardHandler> supplyGuard)
    {
        this.route = route;
        this.supplyGuard = supplyGuard;
        this.identityMatcher = IDENTITY_PATTERN.matcher("");
        this.id = route.id;
        this.with = (AguiA2aWithConfig) route.with;
        this.guarded = route.guarded;
        this.authorized = route.authorized;

        final ConditionConfig when = route.when.isEmpty() ? null : route.when.get(0);
        this.agent = when != null ? ((AguiA2aConditionConfig) when).agent : null;
    }

    public boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    public String resolveIdentity(
        long authorization)
    {
        String identity = null;

        if (with != null && with.identity != null && identityMatcher.reset(with.identity).matches())
        {
            final GuardHandler guard = supplyGuard.apply(route.resolveId.applyAsLong(identityMatcher.group(1)));
            identity = guard != null ? guard.identity(authorization) : null;
        }

        return identity;
    }

    public boolean matchesAgent(
        String agent)
    {
        return this.agent == null || this.agent.equals(agent);
    }

    public String resolveAgent(
        String agent)
    {
        return with != null && with.agent != null ? with.agent : agent;
    }
}
