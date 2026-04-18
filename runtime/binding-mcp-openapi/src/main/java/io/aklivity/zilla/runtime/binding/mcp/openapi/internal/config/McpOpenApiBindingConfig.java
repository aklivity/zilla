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

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenApiOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class McpOpenApiBindingConfig
{
    public final long id;
    public final String namespace;
    public final String qname;
    public final KindConfig kind;
    public final McpOpenApiOptionsConfig options;
    public final List<McpOpenApiRouteConfig> routes;

    public McpOpenApiBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.kind = binding.kind;
        this.options = (McpOpenApiOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(McpOpenApiRouteConfig::new)
            .collect(toList());
    }

    public McpOpenApiRouteConfig resolve(
        long authorization,
        String tool,
        String resource)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(tool, resource))
            .findFirst()
            .orElse(null);
    }
}
