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
package io.aklivity.zilla.runtime.binding.a2a.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.config.binding.a2a.A2aConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class A2aProxyRouteConfig
{
    public final long id;
    public final Set<String> agents;
    public final List<String> roles;
    public final List<Long> guardIds;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public A2aProxyRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.agents = route.when.stream()
            .map(A2aConditionConfig.class::cast)
            .filter(c -> c.agent != null)
            .flatMap(c -> c.agent.stream())
            .collect(toCollection(LinkedHashSet::new));
        this.roles = route.guarded.stream()
            .flatMap(g -> g.roles.stream())
            .distinct()
            .collect(toList());
        this.guardIds = route.guarded.stream()
            .map(g -> g.id)
            .collect(toList());
        this.authorized = route.authorized;
    }

    public boolean matches(
        String agent)
    {
        return agents.isEmpty() || agent != null && agents.contains(agent);
    }

    public boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }
}
