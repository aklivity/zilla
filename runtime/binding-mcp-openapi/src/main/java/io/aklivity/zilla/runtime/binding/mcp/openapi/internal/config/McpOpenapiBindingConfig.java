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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public final class McpOpenapiBindingConfig
{
    public final long id;
    public final String namespace;
    public final String qname;
    public final KindConfig kind;
    public final McpOpenapiOptionsConfig options;
    public final List<McpOpenapiRouteConfig> routes;

    public final ToLongFunction<String> resolveId;
    public final LongFunction<CatalogHandler> supplyCatalog;
    public final ToIntFunction<String> supplyTypeId;
    public final ToLongBiFunction<NamespaceConfig, BindingConfig> supplyBindingId;
    public final LongFunction<String> supplyQName;

    public transient McpOpenapiCompositeConfig composite;

    public McpOpenapiBindingConfig(
        EngineContext context,
        BindingConfig binding)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.kind = binding.kind;
        this.options = (McpOpenapiOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(McpOpenapiRouteConfig::new)
            .collect(toList());

        this.resolveId = binding.resolveId;
        this.supplyBindingId = context::supplyBindingId;
        this.supplyCatalog = context::supplyCatalog;
        this.supplyTypeId = context::supplyTypeId;
        this.supplyQName = context::supplyQName;

        if (options != null)
        {
            final McpOpenapiAuthorizationConfig authorization = options.authorization;
            if (authorization != null)
            {
                final long namespacedId = binding.resolveId.applyAsLong(authorization.name);
                authorization.qname = context.supplyQName(namespacedId);
            }
        }
    }
}
