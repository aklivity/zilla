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
import java.util.function.UnaryOperator;

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aConditionConfig;
import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aWithConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class AguiA2aRouteConfig
{
    public final long id;
    public final String agent;
    public final AguiA2aWithConfig with;
    public final List<GuardedConfig> guarded;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public AguiA2aRouteConfig(
        RouteConfig route)
    {
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
