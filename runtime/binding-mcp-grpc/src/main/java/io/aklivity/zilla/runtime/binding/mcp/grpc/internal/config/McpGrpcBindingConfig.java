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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.types.McpCapabilities.SERVER_TOOLS;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcAuthorizationConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class McpGrpcBindingConfig
{
    private static final String CREDENTIALS_TOKEN = "{credentials}";
    private static final String IDENTITY_TOKEN = "{identity}";
    private static final String LATEST_VERSION = "latest";

    public final long id;
    public final String qname;
    public final McpGrpcOptionsConfig options;
    public final List<McpGrpcRouteConfig> routes;
    public final GuardHandler guard;
    public final String exit;
    public final String catalogQname;
    public final ToLongBiFunction<NamespaceConfig, BindingConfig> supplyBindingId;

    public transient McpGrpcCompositeConfig composite;

    private final EngineContext context;
    private final ToLongFunction<String> resolveId;

    private McpGrpcToolRegistry registry;
    private byte[] toolsListJson;

    public McpGrpcBindingConfig(
        BindingConfig binding,
        EngineContext context,
        String clientExit)
    {
        this.id = binding.id;
        this.qname = binding.qname;
        this.options = (McpGrpcOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(McpGrpcRouteConfig::new)
            .collect(toList());
        this.context = context;
        this.resolveId = binding.resolveId;
        this.supplyBindingId = context::supplyBindingId;
        this.exit = clientExit.indexOf(':') >= 0 ? clientExit : "%s:%s".formatted(binding.namespace, clientExit);
        this.catalogQname = context.supplyQName(resolveId.applyAsLong(options.catalog.name));

        GuardHandler guard = null;
        final McpGrpcAuthorizationConfig authorization = options.authorization;
        if (authorization != null)
        {
            final long guardId = resolveId.applyAsLong(authorization.name);
            authorization.qname = context.supplyQName(guardId);
            guard = context.supplyGuard(guardId);
        }
        this.guard = guard;
    }

    public int serverCapabilities()
    {
        return SERVER_TOOLS.value();
    }

    public McpGrpcTool resolveTool(
        String name,
        long authorization)
    {
        final McpGrpcToolRegistry registry = registry();
        final McpGrpcTool tool = registry != null ? registry.tool(name) : null;

        return tool != null && (tool.route == null || tool.route.authorized(authorization)) ? tool : null;
    }

    public byte[] toolsListJson()
    {
        if (toolsListJson == null)
        {
            final McpGrpcToolRegistry registry = registry();
            toolsListJson = McpGrpcToolsList.toJson(registry != null ? registry.tools() : List.of());
        }

        return toolsListJson;
    }

    public List<String> denied()
    {
        final McpGrpcToolRegistry registry = registry();
        return registry != null ? registry.denied() : List.of();
    }

    public void resolveCredentials(
        long authorization,
        Map<String, String> headers)
    {
        final McpGrpcAuthorizationConfig config = options.authorization;
        if (config != null && guard != null)
        {
            final String credentials = guard.credentials(authorization);
            final String identity = guard.identity(authorization);
            for (Map.Entry<String, String> entry : config.headers.entrySet())
            {
                final String value = substitute(entry.getValue(), credentials, identity);
                if (value != null)
                {
                    headers.put(entry.getKey(), value);
                }
            }
        }
    }

    private McpGrpcToolRegistry registry()
    {
        if (registry == null)
        {
            final ProtobufSchema schema = resolveSchema();
            if (schema != null)
            {
                registry = McpGrpcToolRegistry.of(schema, options.tools, routes);
            }
        }

        return registry;
    }

    private ProtobufSchema resolveSchema()
    {
        ProtobufSchema schema = null;

        final CatalogHandler handler = context.supplyCatalog(resolveId.applyAsLong(options.catalog.name));
        if (handler != null)
        {
            final String version = options.catalog.version != null ? options.catalog.version : LATEST_VERSION;
            final int schemaId = handler.resolve(options.catalog.subject, version);
            final String text = schemaId != CatalogHandler.NO_SCHEMA_ID ? handler.resolve(schemaId) : null;
            schema = text != null ? Protobuf.schema(text) : null;
        }

        return schema;
    }

    private static String substitute(
        String template,
        String credentials,
        String identity)
    {
        String value = template;

        if (value.contains(CREDENTIALS_TOKEN))
        {
            value = credentials != null ? value.replace(CREDENTIALS_TOKEN, credentials) : null;
        }

        if (value != null && value.contains(IDENTITY_TOKEN))
        {
            value = identity != null ? value.replace(IDENTITY_TOKEN, identity) : null;
        }

        return value;
    }
}
