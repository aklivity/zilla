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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mcp.config.McpElicitationConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpBindingConfig
{
    private static final String HTTP_HEADER_AUTHORITY = ":authority";
    private static final String HTTP_HEADER_PATH = ":path";

    public final long id;
    public final McpOptionsConfig options;

    private final List<McpRouteConfig> routes;

    public McpBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.options = (McpOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(McpRouteConfig::new)
            .collect(Collectors.toList());
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

    public String resolveRedirectUri(
        HttpBeginExFW httpBeginEx)
    {
        String redirectUri = null;
        if (httpBeginEx != null)
        {
            final HttpHeaderFW authorityHeader = httpBeginEx.headers()
                .matchFirst(h -> HTTP_HEADER_AUTHORITY.equals(h.name().asString()));
            final HttpHeaderFW pathHeader = httpBeginEx.headers()
                .matchFirst(h -> HTTP_HEADER_PATH.equals(h.name().asString()));
            if (authorityHeader != null && pathHeader != null)
            {
                final String authority = authorityHeader.value().asString();
                final String path = pathHeader.value().asString();
                final int queryAt = path.indexOf('?');
                final String pathOnly = queryAt >= 0 ? path.substring(0, queryAt) : path;
                final McpElicitationConfig elicitation = options != null ? options.elicitation : null;
                final String callback = elicitation != null
                    ? elicitation.callback
                    : McpElicitationConfig.DEFAULT_CALLBACK_PATH;
                redirectUri = "https://" + authority + pathOnly + "/" + callback;
            }
        }
        return redirectUri;
    }
}
