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
package io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.config;

import java.util.List;

import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class McpAsyncApiRouteConfig
{
    public final long id;
    public final List<McpAsyncApiConditionConfig> when;
    public final McpAsyncApiWithConfig with;

    public McpAsyncApiRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(McpAsyncApiConditionConfig.class::cast)
            .toList();
        this.with = (McpAsyncApiWithConfig) route.with;
    }

    public boolean matches(
        String operation)
    {
        return when.isEmpty() || when.stream().anyMatch(c -> c.matches(operation));
    }
}
