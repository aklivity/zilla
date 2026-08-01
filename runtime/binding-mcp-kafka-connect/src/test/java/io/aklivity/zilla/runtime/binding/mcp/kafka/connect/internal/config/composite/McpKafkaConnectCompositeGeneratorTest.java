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
package io.aklivity.zilla.runtime.binding.mcp.kafka.connect.internal.config.composite;

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

import io.aklivity.zilla.config.binding.mcp.kafka.connect.McpKafkaConnectConditionConfig;
import io.aklivity.zilla.config.binding.mcp.kafka.connect.McpKafkaConnectOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiCatalogConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiConditionConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiSpecificationConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiWithConfig;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfig;
import io.aklivity.zilla.config.catalog.inline.InlineSchemaConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.CatalogConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.connect.internal.config.McpKafkaConnectBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.connect.internal.config.McpKafkaConnectCompositeConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class McpKafkaConnectCompositeGeneratorTest
{
    private static final List<String> TOOLS = List.of(
        "list_connectors", "create_connector", "describe_connector", "delete_connector",
        "describe_connector_config", "update_connector_config", "validate_connector_config",
        "describe_connector_status", "restart_connector", "pause_connector", "resume_connector", "stop_connector",
        "list_connector_tasks", "restart_connector_task",
        "describe_connector_offsets", "alter_connector_offsets", "reset_connector_offsets",
        "list_connector_plugins");

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    private McpKafkaConnectCompositeGenerator generator;

    @Before
    public void init()
    {
        when(context.supplyBindingId(any(), any())).thenReturn(42L);

        generator = new McpKafkaConnectCompositeGenerator();
    }

    @Test
    public void shouldGenerateComposite()
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp-kafka-connect")
            .kind(CLIENT)
            .options(McpKafkaConnectOptionsConfig.builder()
                .server("http://localhost:8083")
                .build())
            .build();

        McpKafkaConnectBindingConfig attached = new McpKafkaConnectBindingConfig(context, binding);

        McpKafkaConnectCompositeConfig composite = generator.generate(attached);

        assertThat(composite.namespaces, hasSize(1));

        NamespaceConfig namespace = composite.namespaces.get(0);
        assertThat(namespace.catalogs, hasSize(2));
        assertThat(namespace.bindings, hasSize(1));

        CatalogConfig catalog = namespace.catalogs.get(0);
        assertThat(catalog.name, equalTo("specs"));
        assertThat(catalog.type, equalTo("inline"));

        InlineOptionsConfig catalogOptions = (InlineOptionsConfig) catalog.options;
        assertThat(catalogOptions.subjects, hasSize(1));

        InlineSchemaConfig schema = catalogOptions.subjects.get(0);
        assertThat(schema.subject, equalTo("kafka-connect"));
        assertThat(schema.version, equalTo("latest"));
        assertThat(schema.schema, containsString("\"operationId\": \"list_connectors\""));
        assertThat(schema.schema, not(containsString("x-mcp-annotations")));

        CatalogConfig overlayCatalog = namespace.catalogs.get(1);
        assertThat(overlayCatalog.name, equalTo("overlays"));
        assertThat(overlayCatalog.type, equalTo("inline"));

        InlineOptionsConfig overlayCatalogOptions = (InlineOptionsConfig) overlayCatalog.options;
        assertThat(overlayCatalogOptions.subjects, hasSize(1));

        InlineSchemaConfig overlaySchema = overlayCatalogOptions.subjects.get(0);
        assertThat(overlaySchema.subject, equalTo("kafka-connect"));
        assertThat(overlaySchema.version, equalTo("latest"));
        assertThat(overlaySchema.schema, containsString("x-mcp-annotations"));
        assertThat(overlaySchema.schema, containsString("List Connectors"));

        BindingConfig mcpOpenapi = namespace.bindings.get(0);
        assertThat(mcpOpenapi.name, equalTo("mcp-openapi0"));
        assertThat(mcpOpenapi.type, equalTo("mcp-openapi"));
        assertThat(mcpOpenapi.kind, equalTo(CLIENT));

        McpOpenapiOptionsConfig mcpOpenapiOptions = (McpOpenapiOptionsConfig) mcpOpenapi.options;
        assertThat(mcpOpenapiOptions.specs, hasSize(1));

        McpOpenapiSpecificationConfig spec = mcpOpenapiOptions.specs.get(0);
        assertThat(spec.label, equalTo("kafka-connect"));
        assertThat(spec.server, equalTo("http://localhost:8083"));
        assertThat(spec.catalogs, hasSize(1));

        McpOpenapiCatalogConfig specCatalog = spec.catalogs.get(0);
        assertThat(specCatalog.name, equalTo("specs"));
        assertThat(specCatalog.subject, equalTo("kafka-connect"));
        assertThat(specCatalog.version, equalTo("latest"));

        McpOpenapiCatalogConfig specOverlay = spec.overlay;
        assertThat(specOverlay, notNullValue());
        assertThat(specOverlay.name, equalTo("overlays"));
        assertThat(specOverlay.subject, equalTo("kafka-connect"));
        assertThat(specOverlay.version, equalTo("latest"));

        assertThat(mcpOpenapi.routes, hasSize(TOOLS.size()));
        for (int i = 0; i < TOOLS.size(); i++)
        {
            RouteConfig route = mcpOpenapi.routes.get(i);
            assertThat(route.when, hasSize(1));

            McpOpenapiConditionConfig when = (McpOpenapiConditionConfig) route.when.get(0);
            assertThat(when.tool, equalTo(TOOLS.get(i)));

            McpOpenapiWithConfig with = (McpOpenapiWithConfig) route.with;
            assertThat(with.spec, equalTo("kafka-connect"));
            assertThat(with.operation, equalTo(TOOLS.get(i)));
        }

        assertThat(composite.routes, hasSize(1));
        assertThat(composite.routes.get(0).id, equalTo(42L));
    }

    @Test
    public void shouldReduceScopeToDeclaredTools()
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp-kafka-connect")
            .kind(CLIENT)
            .options(McpKafkaConnectOptionsConfig.builder()
                .server("http://localhost:8083")
                .build())
            .route()
                .when(McpKafkaConnectConditionConfig.builder()
                    .tool(List.of("list_*"))
                    .build())
                .build()
            .route()
                .when(McpKafkaConnectConditionConfig.builder()
                    .tool(List.of("describe_connector"))
                    .build())
                .build()
            .build();

        McpKafkaConnectBindingConfig attached = new McpKafkaConnectBindingConfig(context, binding);

        McpKafkaConnectCompositeConfig composite = generator.generate(attached);

        BindingConfig mcpOpenapi = composite.namespaces.get(0).bindings.get(0);
        List<String> routedTools = mcpOpenapi.routes.stream()
            .map(route -> ((McpOpenapiConditionConfig) route.when.get(0)).tool)
            .toList();

        assertThat(routedTools, hasSize(4));
        assertThat(routedTools, equalTo(List.of(
            "list_connectors", "describe_connector", "list_connector_tasks", "list_connector_plugins")));
    }

    @Test
    public void shouldMatchAnyToolNameWithinSingleRouteAllowlist()
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp-kafka-connect")
            .kind(CLIENT)
            .options(McpKafkaConnectOptionsConfig.builder()
                .server("http://localhost:8083")
                .build())
            .route()
                .when(McpKafkaConnectConditionConfig.builder()
                    .tool(List.of("list_connectors", "describe_connector"))
                    .build())
                .build()
            .build();

        McpKafkaConnectBindingConfig attached = new McpKafkaConnectBindingConfig(context, binding);

        McpKafkaConnectCompositeConfig composite = generator.generate(attached);

        BindingConfig mcpOpenapi = composite.namespaces.get(0).bindings.get(0);
        List<String> routedTools = mcpOpenapi.routes.stream()
            .map(route -> ((McpOpenapiConditionConfig) route.when.get(0)).tool)
            .toList();

        assertThat(routedTools, hasSize(2));
        assertThat(routedTools, equalTo(List.of("list_connectors", "describe_connector")));
    }

    @Test
    public void shouldGuardDeclaredRoute()
    {
        when(context.supplyQName(eq(3L))).thenReturn("test:jwt0");

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp-kafka-connect")
            .kind(CLIENT)
            .options(McpKafkaConnectOptionsConfig.builder()
                .server("http://localhost:8083")
                .build())
            .route()
                .when(McpKafkaConnectConditionConfig.builder()
                    .tool(List.of("create_connector"))
                    .build())
                .guarded()
                    .name("jwt0")
                    .role("write")
                    .build()
                .build()
            .build();
        binding.resolveId = name -> "jwt0".equals(name) ? 3L : 2L;

        McpKafkaConnectBindingConfig attached = new McpKafkaConnectBindingConfig(context, binding);

        McpKafkaConnectCompositeConfig composite = generator.generate(attached);

        BindingConfig mcpOpenapi = composite.namespaces.get(0).bindings.get(0);
        assertThat(mcpOpenapi.routes, hasSize(1));

        RouteConfig route = mcpOpenapi.routes.get(0);
        assertThat(((McpOpenapiConditionConfig) route.when.get(0)).tool, equalTo("create_connector"));

        List<GuardedConfig> guarded = route.guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("test:jwt0"));
        assertThat(guarded.get(0).roles, equalTo(List.of("write")));
    }

    @Test
    public void shouldGenerateCompositeForProxyKind()
    {
        when(context.supplyQName(eq(5L))).thenReturn("test:http0");

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp-kafka-connect")
            .kind(PROXY)
            .exit("http0")
            .build();
        binding.routes.stream()
            .filter(route -> "http0".equals(route.exit))
            .findFirst()
            .orElseThrow()
            .id = 5L;

        McpKafkaConnectBindingConfig attached = new McpKafkaConnectBindingConfig(context, binding);

        McpKafkaConnectCompositeConfig composite = generator.generate(attached);

        BindingConfig mcpOpenapi = composite.namespaces.get(0).bindings.get(0);
        assertThat(mcpOpenapi.kind, equalTo(PROXY));

        McpOpenapiOptionsConfig mcpOpenapiOptions = (McpOpenapiOptionsConfig) mcpOpenapi.options;
        McpOpenapiSpecificationConfig spec = mcpOpenapiOptions.specs.get(0);
        assertThat(spec.server, nullValue());

        RouteConfig exitRoute = mcpOpenapi.routes.stream()
            .filter(route -> route.exit != null)
            .findFirst()
            .orElse(null);
        assertThat(exitRoute, notNullValue());
        assertThat(exitRoute.exit, equalTo("test:http0"));
        assertThat(exitRoute.with, nullValue());

        List<String> routedTools = mcpOpenapi.routes.stream()
            .filter(route -> route.with != null)
            .map(route -> ((McpOpenapiConditionConfig) route.when.get(0)).tool)
            .toList();
        assertThat(routedTools, equalTo(TOOLS));
    }

    @Test
    public void shouldExposeNoRoutesWhenNoToolMatches()
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp-kafka-connect")
            .kind(CLIENT)
            .options(McpKafkaConnectOptionsConfig.builder()
                .server("http://localhost:8083")
                .build())
            .route()
                .when(McpKafkaConnectConditionConfig.builder()
                    .tool(List.of("nonexistent_tool"))
                    .build())
                .build()
            .build();

        McpKafkaConnectBindingConfig attached = new McpKafkaConnectBindingConfig(context, binding);

        McpKafkaConnectCompositeConfig composite = generator.generate(attached);

        BindingConfig mcpOpenapi = composite.namespaces.get(0).bindings.get(0);
        assertThat(mcpOpenapi.routes, empty());
    }
}
