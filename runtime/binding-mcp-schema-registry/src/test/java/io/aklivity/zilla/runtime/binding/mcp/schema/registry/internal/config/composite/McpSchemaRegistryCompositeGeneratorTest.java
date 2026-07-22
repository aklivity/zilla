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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryCompositeConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class McpSchemaRegistryCompositeGeneratorTest
{
    private static final List<String> TOOLS = List.of(
        "list_subjects", "describe_subject", "get_schema", "register_schema", "delete_subject",
        "delete_schema_version", "check_compatibility", "get_compatibility", "set_compatibility", "list_contexts");

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    private McpSchemaRegistryCompositeGenerator generator;

    @Before
    public void init()
    {
        when(context.supplyBindingId(any(), any())).thenReturn(42L);

        generator = new McpSchemaRegistryCompositeGenerator();
    }

    @Test
    public void shouldGenerateComposite()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp_schema_registry")
            .kind(CLIENT)
            .options(new McpSchemaRegistryOptionsConfig("http://localhost:8080"))
            .build();

        McpSchemaRegistryBindingConfig attached = new McpSchemaRegistryBindingConfig(context, binding);

        McpSchemaRegistryCompositeConfig composite = generator.generate(attached);

        assertThat(composite.namespaces, hasSize(1));

        NamespaceConfig namespace = composite.namespaces.get(0);
        assertThat(namespace.catalogs, hasSize(1));
        assertThat(namespace.bindings, hasSize(1));

        CatalogConfig catalog = namespace.catalogs.get(0);
        assertThat(catalog.name, equalTo("catalog0"));
        assertThat(catalog.type, equalTo("inline"));

        InlineOptionsConfig catalogOptions = (InlineOptionsConfig) catalog.options;
        assertThat(catalogOptions.subjects, hasSize(1));

        InlineSchemaConfig schema = catalogOptions.subjects.get(0);
        assertThat(schema.subject, equalTo("schemaregistry"));
        assertThat(schema.version, equalTo("latest"));
        assertThat(schema.schema, containsString("\"operationId\":\"list_subjects\""));

        BindingConfig mcpOpenapi = namespace.bindings.get(0);
        assertThat(mcpOpenapi.name, equalTo("mcp_openapi0"));
        assertThat(mcpOpenapi.type, equalTo("mcp_openapi"));
        assertThat(mcpOpenapi.kind, equalTo(CLIENT));

        McpOpenapiOptionsConfig mcpOpenapiOptions = (McpOpenapiOptionsConfig) mcpOpenapi.options;
        assertThat(mcpOpenapiOptions.specs, hasSize(1));

        McpOpenapiSpecificationConfig spec = mcpOpenapiOptions.specs.get(0);
        assertThat(spec.label, equalTo("schemaregistry"));
        assertThat(spec.server, equalTo("http://localhost:8080"));
        assertThat(spec.catalogs, hasSize(1));

        McpOpenapiCatalogConfig specCatalog = spec.catalogs.get(0);
        assertThat(specCatalog.name, equalTo("catalog0"));
        assertThat(specCatalog.subject, equalTo("schemaregistry"));
        assertThat(specCatalog.version, equalTo("latest"));

        assertThat(mcpOpenapi.routes, hasSize(TOOLS.size()));
        for (int i = 0; i < TOOLS.size(); i++)
        {
            RouteConfig route = mcpOpenapi.routes.get(i);
            assertThat(route.when, hasSize(1));

            McpOpenapiConditionConfig when = (McpOpenapiConditionConfig) route.when.get(0);
            assertThat(when.tool, equalTo(TOOLS.get(i)));

            McpOpenapiWithConfig with = (McpOpenapiWithConfig) route.with;
            assertThat(with.spec, equalTo("schemaregistry"));
            assertThat(with.operation, equalTo(TOOLS.get(i)));
        }

        assertThat(composite.routes, hasSize(1));
        assertThat(composite.routes.get(0).id, equalTo(42L));
    }

    @Test
    public void shouldReduceScopeToDeclaredTools()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp_schema_registry")
            .kind(CLIENT)
            .options(new McpSchemaRegistryOptionsConfig("http://localhost:8080"))
            .route()
                .when(McpSchemaRegistryConditionConfig.builder()
                    .tool("list_*")
                    .build())
                .build()
            .route()
                .when(McpSchemaRegistryConditionConfig.builder()
                    .tool("get_schema")
                    .build())
                .build()
            .build();

        McpSchemaRegistryBindingConfig attached = new McpSchemaRegistryBindingConfig(context, binding);

        McpSchemaRegistryCompositeConfig composite = generator.generate(attached);

        BindingConfig mcpOpenapi = composite.namespaces.get(0).bindings.get(0);
        List<String> routedTools = mcpOpenapi.routes.stream()
            .map(route -> ((McpOpenapiConditionConfig) route.when.get(0)).tool)
            .toList();

        assertThat(routedTools, hasSize(3));
        assertThat(routedTools, equalTo(List.of("list_subjects", "get_schema", "list_contexts")));
    }

    @Test
    public void shouldGuardDeclaredRoute()
    {
        when(context.supplyQName(eq(3L))).thenReturn("test:jwt0");

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp_schema_registry")
            .kind(CLIENT)
            .options(new McpSchemaRegistryOptionsConfig("http://localhost:8080"))
            .route()
                .when(McpSchemaRegistryConditionConfig.builder()
                    .tool("register_schema")
                    .build())
                .guarded()
                    .name("jwt0")
                    .role("write")
                    .build()
                .build()
            .build();
        binding.resolveId = name -> "jwt0".equals(name) ? 3L : 2L;

        McpSchemaRegistryBindingConfig attached = new McpSchemaRegistryBindingConfig(context, binding);

        McpSchemaRegistryCompositeConfig composite = generator.generate(attached);

        BindingConfig mcpOpenapi = composite.namespaces.get(0).bindings.get(0);
        assertThat(mcpOpenapi.routes, hasSize(1));

        RouteConfig route = mcpOpenapi.routes.get(0);
        assertThat(((McpOpenapiConditionConfig) route.when.get(0)).tool, equalTo("register_schema"));

        List<GuardedConfig> guarded = route.guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("test:jwt0"));
        assertThat(guarded.get(0).roles, equalTo(List.of("write")));
    }

    @Test
    public void shouldExposeNoRoutesWhenNoToolMatches()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp_schema_registry")
            .kind(CLIENT)
            .options(new McpSchemaRegistryOptionsConfig("http://localhost:8080"))
            .route()
                .when(McpSchemaRegistryConditionConfig.builder()
                    .tool("nonexistent_tool")
                    .build())
                .build()
            .build();

        McpSchemaRegistryBindingConfig attached = new McpSchemaRegistryBindingConfig(context, binding);

        McpSchemaRegistryCompositeConfig composite = generator.generate(attached);

        BindingConfig mcpOpenapi = composite.namespaces.get(0).bindings.get(0);
        assertThat(mcpOpenapi.routes, empty());
    }
}
