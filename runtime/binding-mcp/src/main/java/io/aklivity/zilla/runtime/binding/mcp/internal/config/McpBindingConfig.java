/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.config.McpElicitationConfig.DEFAULT_CALLBACK_PATH;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class McpBindingConfig
{
    private static final String HTTP_HEADER_AUTHORITY = ":authority";
    private static final String HTTP_HEADER_PATH = ":path";

    public final long id;
    public final McpOptionsConfig options;
    public final GuardHandler guard;
    public final String credentials;
    public final McpProxyCache cache;
    public final Map<String, McpProxySession> sessions;
    public final Map<String, McpRouteConfig> routeByPrefix;
    public final McpAggregateRoute[] aggregateRoutes;

    private final List<McpRouteConfig> routes;

    public McpBindingConfig(
        BindingConfig binding,
        McpConfiguration config,
        EngineContext context)
    {
        this.id = binding.id;
        this.options = (McpOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(McpRouteConfig::new)
            .collect(Collectors.toList());

        final Map<String, McpRouteConfig> routeByPrefix = new LinkedHashMap<>();
        if (routes.size() > 1)
        {
            final List<String> toolkits = routes.stream()
                .map(McpRouteConfig::toolkit)
                .collect(Collectors.toList());
            final Map<String, String> prefixesByToolkit = McpAggregateEventId.computePrefixes(toolkits);
            for (McpRouteConfig route : routes)
            {
                final String prefix = prefixesByToolkit.get(route.toolkit());
                routeByPrefix.put(prefix, route);
            }
        }
        this.routeByPrefix = routeByPrefix;

        this.aggregateRoutes = routeByPrefix.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new McpAggregateRoute(e.getKey(), e.getValue().id))
            .toArray(McpAggregateRoute[]::new);

        this.guard = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.name)
            .map(binding.resolveId::applyAsLong)
            .map(context::supplyGuard)
            .orElse(null);

        this.credentials = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.credentials)
            .filter(c -> !c.isEmpty())
            .orElse(null);

        this.cache = Optional.ofNullable(options)
            .map(o -> o.cache)
            .map(cache -> new McpProxyCache(binding, config, context, cache))
            .orElse(null);
        this.sessions = new Object2ObjectHashMap<>();
    }

    public McpRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public int serverCapabilities(
        long authorization)
    {
        int bits = 0;
        for (McpRouteConfig route : routes)
        {
            if (route.authorized(authorization))
            {
                bits |= route.serverCapabilities();
            }
        }
        return bits;
    }

    public McpRouteConfig resolve(
        McpBeginExFW beginEx,
        long authorization)
    {
        final String capability = McpRouteConfig.capabilityOf(beginEx);
        final String identifier = McpRouteConfig.identifierOf(beginEx);

        McpRouteConfig resolved = null;

        if (capability == null)
        {
            resolved = resolve(authorization);
        }
        else if (identifier != null)
        {
            for (McpRouteConfig route : routes)
            {
                if (route.authorized(authorization) && route.matches(capability, identifier))
                {
                    resolved = route;
                    break;
                }
            }
        }
        else
        {
            for (McpRouteConfig route : routes)
            {
                if (route.authorized(authorization) && route.serves(capability))
                {
                    resolved = route;
                    break;
                }
            }
        }

        return resolved;
    }

    public List<Long> resolveAll(
        long authorization)
    {
        final List<Long> result = new ArrayList<>();
        for (McpRouteConfig route : routes)
        {
            if (route.authorized(authorization))
            {
                result.add(route.id);
            }
        }
        return result;
    }

    public List<McpRouteConfig> resolveAll(
        McpBeginExFW beginEx,
        long authorization)
    {
        final String capability = McpRouteConfig.capabilityOf(beginEx);
        final String identifier = McpRouteConfig.identifierOf(beginEx);
        final List<McpRouteConfig> result = new ArrayList<>();

        if (capability != null && identifier == null)
        {
            for (McpRouteConfig route : routes)
            {
                if (route.authorized(authorization) && route.serves(capability))
                {
                    result.add(route);
                }
            }
        }

        return result;
    }

    public String resolveRedirectURI(
        HttpBeginExFW httpBeginEx)
    {
        String redirectURI = null;
        if (httpBeginEx != null)
        {
            final String authority = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_AUTHORITY.equals(h.name().asString())))
                .map(h -> h.value().asString())
                .orElse(null);
            final String path = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_PATH.equals(h.name().asString())))
                .map(h -> h.value().asString())
                .orElse(null);
            if (authority != null && path != null)
            {
                final int queryAt = path.indexOf('?');
                final String pathOnly = queryAt >= 0 ? path.substring(0, queryAt) : path;
                final String callback = Optional.ofNullable(options)
                    .map(o -> o.elicitation)
                    .map(e -> e.callback)
                    .orElse(DEFAULT_CALLBACK_PATH);
                redirectURI = "https://" + authority + pathOnly + "/" + callback;
            }
        }
        return redirectURI;
    }
}
