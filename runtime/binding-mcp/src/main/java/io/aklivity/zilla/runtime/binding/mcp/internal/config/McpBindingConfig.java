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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpBindingConfig
{
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

    public long resolveRoute(
        long authorization,
        String kind)
    {
        long resolvedId = -1L;

        for (McpRouteConfig route : routes)
        {
            if (route.authorized(authorization) && route.matches(kind))
            {
                resolvedId = route.id;
                break;
            }
        }

        return resolvedId;
    }
}
