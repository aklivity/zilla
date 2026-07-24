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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.http.McpHttpConditionConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpWithConfig;
import io.aklivity.zilla.config.engine.GenericRouteConfigBuilder;
import io.aklivity.zilla.config.engine.RouteConfig;

public class McpHttpRouteConfigTest
{
    private static McpHttpRouteConfig route(
        String tool,
        String resource,
        boolean withMapping)
    {
        GenericRouteConfigBuilder<RouteConfig> builder = RouteConfig.builder();
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
                .build();
        }
        return new McpHttpRouteConfig(builder.exit("http0").build());
    }

    @Test
    public void shouldApplyUnscopedGuardOnlyRouteToAnyTool()
    {
        McpHttpRouteConfig route = route(null, null, false);

        assertTrue(route.appliesToTool("create_pr"));
        assertTrue(route.appliesToTool("search_code"));
    }

    @Test
    public void shouldApplyUnscopedGuardOnlyRouteToAnyResource()
    {
        McpHttpRouteConfig route = route(null, null, false);

        assertTrue(route.appliesToResource("order"));
    }

    @Test
    public void shouldApplyScopedGuardOnlyRouteToItsOwnToolOnly()
    {
        McpHttpRouteConfig route = route("create_pr", null, false);

        assertTrue(route.appliesToTool("create_pr"));
        assertFalse(route.appliesToTool("search_code"));
        assertFalse(route.appliesToResource("create_pr"));
    }

    @Test
    public void shouldNotApplyMappingRouteToOtherToolNames()
    {
        McpHttpRouteConfig route = route("create_pr", null, true);

        assertTrue(route.appliesToTool("create_pr"));
        assertFalse(route.appliesToTool("search_code"));
    }

    @Test
    public void shouldResolveHeaderWhenArgumentPresent()
    {
        McpHttpRouteConfig route = new McpHttpRouteConfig(RouteConfig.builder()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/notifications")
                .header("x-trace-id", "${args.trace.id}")
                .build()
            .build());

        Map<String, String> args = Map.of("trace.id", "trace-abc");
        Map<String, String> resolved = route.resolveHeaders(args, Map.of());

        assertThat(resolved.get("x-trace-id"), equalTo("trace-abc"));
    }

    @Test
    public void shouldOmitHeaderWhenArgumentAbsent()
    {
        McpHttpRouteConfig route = new McpHttpRouteConfig(RouteConfig.builder()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/notifications")
                .header("x-trace-id", "${args.trace.id}")
                .build()
            .build());

        Map<String, String> resolved = route.resolveHeaders(Map.of(), Map.of());

        assertThat(resolved.get("x-trace-id"), nullValue());
    }

    @Test
    public void shouldCollectNestedArgAccessorFromHeader()
    {
        McpHttpRouteConfig route = new McpHttpRouteConfig(RouteConfig.builder()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/notifications")
                .header("x-trace-id", "${args.trace.id}")
                .build()
            .build());

        assertThat(route.argAccessors, equalTo(List.of("trace.id")));
    }

    @Test
    public void shouldAggregateCookiesWhenAllPresent()
    {
        McpHttpRouteConfig route = new McpHttpRouteConfig(RouteConfig.builder()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/notifications")
                .cookie("a", "${args.a}")
                .cookie("b", "${args.b}")
                .build()
            .build());

        Map<String, String> args = Map.of("a", "1", "b", "2");
        String resolved = route.resolveCookies(args, Map.of());

        assertThat(resolved, equalTo("a=1; b=2"));
    }

    @Test
    public void shouldOmitOneCookieWhenPartiallyAbsent()
    {
        McpHttpRouteConfig route = new McpHttpRouteConfig(RouteConfig.builder()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/notifications")
                .cookie("a", "${args.a}")
                .cookie("b", "${args.b}")
                .build()
            .build());

        Map<String, String> args = Map.of("a", "1");
        String resolved = route.resolveCookies(args, Map.of());

        assertThat(resolved, equalTo("a=1"));
    }

    @Test
    public void shouldOmitCookieHeaderWhenAllAbsent()
    {
        McpHttpRouteConfig route = new McpHttpRouteConfig(RouteConfig.builder()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/notifications")
                .cookie("a", "${args.a}")
                .cookie("b", "${args.b}")
                .build()
            .build());

        String resolved = route.resolveCookies(Map.of(), Map.of());

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldCollectArgAccessorFromCookie()
    {
        McpHttpRouteConfig route = new McpHttpRouteConfig(RouteConfig.builder()
            .with(McpHttpWithConfig::builder)
                .header(":path", "/notifications")
                .cookie("session", "${args.session.id}")
                .build()
            .build());

        assertThat(route.argAccessors, equalTo(List.of("session.id")));
    }
}
