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
package io.aklivity.zilla.runtime.binding.tcp.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Predicate;

import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class TcpRouteConfig
{
    public final long id;

    private final List<TcpConditionMatcher> when;
    private final LongObjectPredicate authorized;

    public TcpRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(TcpConditionConfig.class::cast)
            .map(TcpConditionMatcher::new)
            .collect(toList());
        this.authorized = route.authorized;
    }

    public boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    public boolean matches(
        InetSocketAddress address)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(address));
    }

    public boolean matchesExplicit(
        InetSocketAddress address)
    {
        return when.stream().anyMatch(m -> m.matches(address));
    }

    public boolean matchesExplicit(
        Predicate<TcpConditionMatcher> predicate)
    {
        return when.stream().anyMatch(predicate);
    }
}
