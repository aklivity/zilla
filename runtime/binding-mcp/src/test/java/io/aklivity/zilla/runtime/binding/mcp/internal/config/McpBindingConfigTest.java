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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig.authorityOf;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig.computeRouteByPrefix;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig.naturalAuthority;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig.pathOf;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig.rolesForTool;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.config.McpConditionConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class McpBindingConfigTest
{
    private static McpRouteConfig route(
        long id,
        String toolkit,
        List<String> tools,
        String guardName,
        List<String> guardRoles)
    {
        RouteConfig config = RouteConfig.builder()
            .exit("test")
            .when(McpConditionConfig.builder()
                .toolkit(toolkit)
                .tools(tools)
                .build())
            .guarded()
                .name(guardName)
                .roles(guardRoles)
                .build()
            .build();
        config.id = id;
        return new McpRouteConfig(config);
    }

    @Test
    public void shouldComputeRouteByPrefixWhenMultipleToolkitRoutes()
    {
        McpRouteConfig bluesky = route(1L, "bluesky", List.of("post_*"), "test0", List.of("write"));
        McpRouteConfig quartz = route(2L, "quartz", List.of("run_*"), "test0", List.of("run"));

        Map<String, McpRouteConfig> routeByPrefix = computeRouteByPrefix(List.of(bluesky, quartz));

        assertThat(routeByPrefix.size(), equalTo(2));
    }

    @Test
    public void shouldSkipToolkitlessRoutesWhenComputingRouteByPrefix()
    {
        McpRouteConfig readRoute = route(1L, null, List.of("read_*"), "test0", List.of("read"));
        McpRouteConfig writeRoute = route(2L, null, List.of("write_*"), "test0", List.of("write"));

        Map<String, McpRouteConfig> routeByPrefix = computeRouteByPrefix(List.of(readRoute, writeRoute));

        assertThat(routeByPrefix.entrySet(), empty());
    }

    @Test
    public void shouldSkipNullToolkitAmongMixedRoutesWhenComputingRouteByPrefix()
    {
        McpRouteConfig bluesky = route(1L, "bluesky", List.of("post_*"), "test0", List.of("write"));
        McpRouteConfig toolkitless = route(2L, null, List.of("read_*"), "test0", List.of("read"));

        Map<String, McpRouteConfig> routeByPrefix = computeRouteByPrefix(List.of(bluesky, toolkitless));

        assertThat(routeByPrefix.size(), equalTo(1));
        assertThat(routeByPrefix.values(), contains(bluesky));
    }

    @Test
    public void shouldReturnRolesForMatchingTool()
    {
        McpRouteConfig readRoute = route(1L, null, List.of("read_*"), "test0", List.of("read"));
        McpRouteConfig writeRoute = route(2L, null, List.of("write_*"), "test1", List.of("write"));
        List<McpRouteConfig> routes = List.of(readRoute, writeRoute);

        assertThat(rolesForTool(routes, "read_file"), hasEntry("test0", List.of("read")));
        assertThat(rolesForTool(routes, "write_file"), hasEntry("test1", List.of("write")));
    }

    @Test
    public void shouldReturnEmptyRolesForNonMatchingTool()
    {
        McpRouteConfig readRoute = route(1L, null, List.of("read_*"), "test0", List.of("read"));

        assertThat(rolesForTool(List.of(readRoute), "delete_file"), equalTo(Map.of()));
    }

    @Test
    public void shouldKeepExplicitPortInAuthority()
    {
        assertThat(authorityOf(URI.create("http://localhost:8080/mcp")), equalTo("localhost:8080"));
    }

    @Test
    public void shouldDefaultHttpPortInAuthority()
    {
        assertThat(authorityOf(URI.create("http://localhost/mcp")), equalTo("localhost:80"));
    }

    @Test
    public void shouldDefaultHttpsPortInAuthority()
    {
        assertThat(authorityOf(URI.create("https://localhost/mcp")), equalTo("localhost:443"));
    }

    @Test
    public void shouldResolvePath()
    {
        assertThat(pathOf(URI.create("http://localhost:8080/mcp")), equalTo("/mcp"));
    }

    @Test
    public void shouldDefaultRootPath()
    {
        assertThat(pathOf(URI.create("http://localhost:8080")), equalTo("/"));
    }

    @Test
    public void shouldStripDefaultHttpsPortFromAuthority()
    {
        assertThat(naturalAuthority("localhost:443", "https"), equalTo("localhost"));
    }

    @Test
    public void shouldKeepNonDefaultPortInAuthority()
    {
        assertThat(naturalAuthority("localhost:8080", "https"), equalTo("localhost:8080"));
    }

    @Test
    public void shouldKeepAuthorityWithoutPort()
    {
        assertThat(naturalAuthority("localhost", "https"), equalTo("localhost"));
    }
}
