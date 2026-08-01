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
package io.aklivity.zilla.runtime.binding.mcp.kafka.connect.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.config.binding.mcp.kafka.connect.McpKafkaConnectOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;

public final class McpKafkaConnectBindingConfig
{
    public final long id;
    public final String namespace;
    public final String qname;
    public final KindConfig kind;
    public final McpKafkaConnectOptionsConfig options;
    public final List<McpKafkaConnectRouteConfig> routes;
    public final String exit;

    public final ToLongBiFunction<NamespaceConfig, BindingConfig> supplyBindingId;
    public final ToLongFunction<String> resolveId;
    public final LongFunction<String> supplyQName;

    public transient McpKafkaConnectCompositeConfig composite;

    public McpKafkaConnectBindingConfig(
        EngineContext context,
        BindingConfig binding)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.kind = binding.kind;
        this.options = (McpKafkaConnectOptionsConfig) binding.options;

        // a top-level exit: shorthand synthesizes its own catch-all last route (empty when, no with,
        // exit set) on this same binding -- exclude it here so it never counts as a declared tool route
        this.routes = binding.routes.stream()
            .filter(route -> route.exit == null)
            .map(McpKafkaConnectRouteConfig::new)
            .collect(toList());

        this.supplyBindingId = context::supplyBindingId;
        this.resolveId = binding.resolveId;
        this.supplyQName = context::supplyQName;

        final RouteConfig exitRoute = binding.routes.stream()
            .filter(route -> route.exit != null)
            .findFirst()
            .orElse(null);
        this.exit = exitRoute != null ? context.supplyQName(exitRoute.id) : null;
    }
}
