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

import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mcp.config.McpConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class McpRouteConfig
{
    public final long id;
    public final McpWithConfig with;
    private final List<McpConditionConfig> when;

    public McpRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.with = McpWithConfig.class.cast(route.with);
        this.when = route.when.stream()
            .map(McpConditionConfig.class::cast)
            .collect(Collectors.toList());
    }

    public boolean authorized(
        long authorization)
    {
        return true;
    }

    public boolean matches(
        String kind)
    {
        return when.isEmpty() ||
            when.stream().anyMatch(c -> c.kind == null || c.kind.equals(kind));
    }
}
