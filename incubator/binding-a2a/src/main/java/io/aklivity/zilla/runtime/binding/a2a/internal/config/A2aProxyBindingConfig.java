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

import java.util.List;
import java.util.Optional;

import io.aklivity.zilla.config.binding.a2a.A2aCacheConfig;
import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class A2aProxyBindingConfig
{
    public final long id;
    public final A2aCacheConfig cache;
    public final GuardHandler guard;
    public final List<A2aProxyRouteConfig> routes;

    public A2aProxyBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        final A2aOptionsConfig options = (A2aOptionsConfig) binding.options;

        this.id = binding.id;
        this.cache = options != null ? options.cache : null;
        this.routes = binding.routes.stream()
            .map(A2aProxyRouteConfig::new)
            .toList();

        this.guard = routes.stream()
            .flatMap(r -> r.guardIds.stream())
            .map(context::supplyGuard)
            .filter(g -> g != null)
            .findFirst()
            .orElseGet(() -> Optional.ofNullable(cache)
                .map(c -> c.authorization)
                .map(a -> a.name)
                .map(binding.resolveId::applyAsLong)
                .map(context::supplyGuard)
                .orElse(null));
    }

    public A2aProxyResolution resolve(
        String agent,
        long authorization)
    {
        A2aProxyResolution resolution = null;
        List<String> forbidden = null;

        for (A2aProxyRouteConfig route : routes)
        {
            if (route.matches(agent))
            {
                if (route.authorized(authorization))
                {
                    resolution = A2aProxyResolution.routed(route);
                    break;
                }
                else if (forbidden == null)
                {
                    forbidden = route.roles;
                }
            }
        }

        if (resolution == null)
        {
            resolution = forbidden != null
                ? A2aProxyResolution.forbidden(forbidden)
                : agent == null
                    ? A2aProxyResolution.agentRequired()
                    : A2aProxyResolution.agentNotFound();
        }

        return resolution;
    }
}
