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

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.config.binding.a2a.A2aConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class A2aClientRouteConfig
{
    public final long id;
    public final Set<String> skills;
    public final Map<String, List<String>> roles;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public A2aClientRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.skills = route.when.stream()
            .map(A2aConditionConfig.class::cast)
            .filter(c -> c.skill != null)
            .flatMap(c -> c.skill.stream())
            .collect(toCollection(LinkedHashSet::new));
        this.roles = route.guarded.stream()
            .filter(g -> !g.roles.isEmpty())
            .collect(toMap(g -> g.name, g -> g.roles, (a, b) -> a, LinkedHashMap::new));
        this.authorized = route.authorized;
    }

    public boolean guardsSkill(
        String skill)
    {
        return skills.contains(skill);
    }

    public boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, UnaryOperator.identity());
    }

    public List<String> requiredRoles()
    {
        return roles.values().stream()
            .flatMap(List::stream)
            .distinct()
            .collect(toList());
    }
}
