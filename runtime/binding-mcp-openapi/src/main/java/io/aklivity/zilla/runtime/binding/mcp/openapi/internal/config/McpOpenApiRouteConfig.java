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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenApiConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenApiWithConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class McpOpenApiRouteConfig
{
    public final long id;
    public final McpOpenApiWithConfig with;
    public final List<McpOpenApiConditionConfig> when;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public McpOpenApiRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.authorized = route.authorized;
        this.when = route.when.stream()
            .map(McpOpenApiConditionConfig.class::cast)
            .collect(toList());
        this.with = (McpOpenApiWithConfig) route.with;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        String tool,
        String resource)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(tool, resource));
    }
}
