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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.composite;

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiConditionConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiOptionsConfig;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfigBuilder;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.GuardedConfigBuilder;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryCompositeConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryRouteConfig;

public final class McpSchemaRegistryCompositeGenerator
{
    private static final String BUNDLED_SPEC_RESOURCE =
        "/io/aklivity/zilla/runtime/binding/mcp/schema/registry/internal/schema/karapace-schema-registry.openapi.json";
    private static final String CATALOG_NAME = "catalog0";
    private static final String BINDING_NAME = "mcp_openapi0";
    private static final String SUBJECT_NAME = "schemaregistry";

    private static final List<String> TOOLS = List.of(
        "list_subjects", "describe_subject", "get_schema", "register_schema", "delete_subject",
        "delete_schema_version", "check_compatibility", "get_compatibility", "set_compatibility");

    private final String bundledSpec;

    public McpSchemaRegistryCompositeGenerator()
    {
        this.bundledSpec = loadBundledSpec();
    }

    public McpSchemaRegistryCompositeConfig generate(
        McpSchemaRegistryBindingConfig binding)
    {
        NamespaceConfig namespace = NamespaceConfig.builder()
            .name("%s/mcp_openapi".formatted(binding.qname))
            .catalog()
                .name(CATALOG_NAME)
                .type("inline")
                .options(InlineOptionsConfig::builder)
                    .schema()
                        .subject(SUBJECT_NAME)
                        .version("latest")
                        .schema(bundledSpec)
                        .build()
                    .build()
                .build()
            .binding()
                .name(BINDING_NAME)
                .type("mcp_openapi")
                .kind(CLIENT)
                .options(McpOpenapiOptionsConfig::builder)
                    .spec()
                        .label(SUBJECT_NAME)
                        .server(binding.options.server)
                        .catalog()
                            .name(CATALOG_NAME)
                            .subject(SUBJECT_NAME)
                            .version("latest")
                            .build()
                        .build()
                    .build()
                .inject(b -> injectRoutes(b, binding))
                .build()
            .build();

        long routeId = binding.supplyBindingId.applyAsLong(namespace, namespace.bindings.get(0));

        return new McpSchemaRegistryCompositeConfig(
            List.of(namespace),
            List.of(new McpSchemaRegistryCompositeRouteConfig(routeId)));
    }

    private <C> BindingConfigBuilder<C> injectRoutes(
        BindingConfigBuilder<C> mcpOpenapi,
        McpSchemaRegistryBindingConfig binding)
    {
        for (String tool : TOOLS)
        {
            McpSchemaRegistryRouteConfig matched = resolveRoute(binding.routes, tool);
            if (binding.routes.isEmpty() || matched != null)
            {
                List<GuardedConfig> guarded = matched != null ? matched.guarded : List.of();
                mcpOpenapi.route()
                    .when(McpOpenapiConditionConfig.builder().tool(tool).build())
                    .with(McpOpenapiWithConfig.builder().spec(SUBJECT_NAME).operation(tool).build())
                    .inject(r -> injectGuarded(r, binding, guarded))
                    .build();
            }
        }
        return mcpOpenapi;
    }

    private static McpSchemaRegistryRouteConfig resolveRoute(
        List<McpSchemaRegistryRouteConfig> routes,
        String tool)
    {
        return routes.stream()
            .filter(route -> route.when.stream().anyMatch(w -> matchesTool(w.tool, tool)))
            .findFirst()
            .orElse(null);
    }

    private static boolean matchesTool(
        String pattern,
        String tool)
    {
        return compileGlob(pattern).matcher(tool).matches();
    }

    private <C> RouteConfigBuilder<C> injectGuarded(
        RouteConfigBuilder<C> route,
        McpSchemaRegistryBindingConfig binding,
        List<GuardedConfig> guarded)
    {
        for (GuardedConfig guard : guarded)
        {
            String qname = binding.supplyQName.apply(binding.resolveId.applyAsLong(guard.name));
            route.guarded()
                .name(qname)
                .inject(g -> injectRoles(g, guard.roles))
                .build();
        }
        return route;
    }

    private <C> GuardedConfigBuilder<C> injectRoles(
        GuardedConfigBuilder<C> guarded,
        List<String> roles)
    {
        roles.forEach(guarded::role);
        return guarded;
    }

    private static Pattern compileGlob(
        String glob)
    {
        StringBuilder regex = new StringBuilder();
        String[] literals = glob.split("\\*", -1);

        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }

            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }

        return Pattern.compile(regex.toString());
    }

    private static String loadBundledSpec()
    {
        String schema;
        try (InputStream input = McpSchemaRegistryCompositeGenerator.class.getResourceAsStream(BUNDLED_SPEC_RESOURCE))
        {
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return schema;
    }
}
