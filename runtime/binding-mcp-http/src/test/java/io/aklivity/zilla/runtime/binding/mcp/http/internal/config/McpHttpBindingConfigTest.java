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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.http.internal.config.McpHttpBindingConfig.argPathValid;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.http.McpHttpConditionConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpWithConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.GenericRouteConfigBuilder;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public class McpHttpBindingConfigTest
{
    private static RouteConfig route(
        int order,
        String tool,
        String resource,
        boolean withMapping,
        boolean authorized)
    {
        GenericRouteConfigBuilder<RouteConfig> builder = RouteConfig.builder().order(order);
        if (tool != null || resource != null)
        {
            builder = builder.when(McpHttpConditionConfig.builder()
                .tool(tool)
                .resource(resource)
                .build());
        }
        if (withMapping)
        {
            builder = builder.with(McpHttpWithConfig::builder)
                .header(":path", "/items")
                .build()
                .exit("http0");
        }
        RouteConfig config = builder.build();
        config.authorized = (auth, identity) -> authorized;
        return config;
    }

    private static McpHttpBindingConfig binding(
        List<RouteConfig> routes)
    {
        BindingConfig config = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp_http")
            .kind(KindConfig.PROXY)
            .routes(routes)
            .build();
        return new McpHttpBindingConfig(config, null);
    }

    @Test
    public void shouldRejectToolWhenGlobalGuardOnlyLayerFails()
    {
        McpHttpBindingConfig binding = binding(List.of(
            route(0, null, null, false, false),
            route(1, "create_pr", null, true, true)));

        assertNull(binding.resolveTool("create_pr", 1L));
    }

    @Test
    public void shouldResolveToolWhenAllApplicableLayersAuthorize()
    {
        McpHttpBindingConfig binding = binding(List.of(
            route(0, null, null, false, true),
            route(1, "create_pr", null, true, true)));

        assertNotNull(binding.resolveTool("create_pr", 1L));
    }

    @Test
    public void shouldRejectResourceWhenScopedGuardOnlyLayerFails()
    {
        McpHttpBindingConfig binding = binding(List.of(
            route(0, null, "order", false, false),
            route(1, null, "order", true, true)));

        assertNull(binding.resolveResourceRoute("order", 1L));
    }

    @Test
    public void shouldAggregateToolGuardedAcrossMappingAndGlobalLayer()
    {
        RouteConfig global = RouteConfig.builder()
            .order(0)
            .guarded().name("test0").roles(List.of("read")).build()
            .build();
        global.authorized = (auth, identity) -> true;

        RouteConfig mapping = RouteConfig.builder()
            .order(1)
            .when(McpHttpConditionConfig.builder()
                .tool("create_pr")
                .build())
            .guarded().name("test1").roles(List.of("write")).build()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/items")
                .build()
            .exit("http0")
            .build();
        mapping.authorized = (auth, identity) -> true;

        McpHttpBindingConfig binding = binding(List.of(global, mapping));

        assertThat(binding.toolGuarded("create_pr"), hasSize(2));
        assertThat(binding.toolGuarded("search_code"), hasSize(1));
    }

    @Test
    public void shouldAggregateResourceGuardedAcrossMappingAndScopedLayer()
    {
        RouteConfig scoped = RouteConfig.builder()
            .order(0)
            .when(McpHttpConditionConfig.builder()
                .resource("order")
                .build())
            .guarded().name("test0").roles(List.of("read")).build()
            .build();
        scoped.authorized = (auth, identity) -> true;

        RouteConfig mapping = RouteConfig.builder()
            .order(1)
            .when(McpHttpConditionConfig.builder()
                .resource("order")
                .build())
            .guarded().name("test1").roles(List.of("write")).build()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/items")
                .build()
            .exit("http0")
            .build();
        mapping.authorized = (auth, identity) -> true;

        McpHttpBindingConfig binding = binding(List.of(scoped, mapping));

        assertThat(binding.resourceGuarded("order"), hasSize(2));
        assertThat(binding.resourceGuarded("other"), empty());
    }

    private static final String FLAT = "{\"type\":\"object\",\"properties\":{\"owner\":{\"type\":\"string\"}}}";
    private static final String NESTED = "{\"type\":\"object\",\"properties\":" +
        "{\"user\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}}}";

    @Test
    public void shouldAcceptPresentFlatProperty()
    {
        assertTrue(argPathValid(FLAT, "owner"));
    }

    @Test
    public void shouldRejectAbsentFlatProperty()
    {
        assertFalse(argPathValid(FLAT, "ownerr"));
    }

    @Test
    public void shouldAcceptPresentNestedProperty()
    {
        assertTrue(argPathValid(NESTED, "user.id"));
    }

    @Test
    public void shouldRejectAbsentNestedProperty()
    {
        assertFalse(argPathValid(NESTED, "user.idd"));
    }

    @Test
    public void shouldAcceptWhenAdditionalPropertiesTrue()
    {
        final String schema = "{\"type\":\"object\",\"additionalProperties\":true," +
            "\"properties\":{\"owner\":{\"type\":\"string\"}}}";
        assertTrue(argPathValid(schema, "anything"));
    }

    @Test
    public void shouldAcceptWhenNoProperties()
    {
        assertTrue(argPathValid("{\"type\":\"object\"}", "owner"));
    }

    @Test
    public void shouldAcceptWhenRef()
    {
        assertTrue(argPathValid("{\"$ref\":\"#/definitions/Owner\"}", "owner"));
    }

    @Test
    public void shouldAcceptWhenComposed()
    {
        final String schema = "{\"allOf\":[{\"type\":\"object\",\"properties\":{\"owner\":{}}}]}";
        assertTrue(argPathValid(schema, "owner"));
    }

    @Test
    public void shouldAcceptWhenSchemaUnresolved()
    {
        assertTrue(argPathValid(null, "owner"));
        assertTrue(argPathValid("", "owner"));
        assertTrue(argPathValid("   ", "owner"));
    }

    @Test
    public void shouldAcceptWhenSchemaMalformed()
    {
        assertTrue(argPathValid("{ not json", "owner"));
    }
}
