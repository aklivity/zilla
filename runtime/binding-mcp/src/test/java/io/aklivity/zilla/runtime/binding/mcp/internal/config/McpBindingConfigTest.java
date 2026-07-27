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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.McpCacheConfig;
import io.aklivity.zilla.config.binding.mcp.McpConditionConfig;
import io.aklivity.zilla.config.binding.mcp.McpOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.McpWithConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.GenericRouteConfigBuilder;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

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

    private static RouteConfig rawRoute(
        long id,
        String withCredentials)
    {
        GenericRouteConfigBuilder<RouteConfig> builder = RouteConfig.builder()
            .exit("test")
            .when(McpConditionConfig.builder()
                .toolkit("alpha")
                .tools(List.of("*"))
                .build());
        if (withCredentials != null)
        {
            builder.with(McpWithConfig.builder()
                .cache()
                    .credentials(withCredentials)
                    .build()
                .build());
        }
        RouteConfig config = builder.build();
        config.id = id;
        return config;
    }

    private static McpBindingConfig binding(
        GuardHandler guard,
        String sharedCredentials,
        List<RouteConfig> routes)
    {
        final McpCacheConfig cacheConfig = McpCacheConfig.builder()
            .store("memory0")
            .authorization()
                .name("test_guard")
                .credentials(sharedCredentials)
                .build()
            .build();
        final McpOptionsConfig options = McpOptionsConfig.builder()
            .cache(cacheConfig)
            .build();

        BindingConfig config = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp")
            .kind(KindConfig.PROXY)
            .options(options)
            .routes(routes)
            .build();
        config.id = 1L;
        config.resolveId = name -> 1L;

        EngineContext context = mock(EngineContext.class);
        when(context.supplyStore(anyLong())).thenReturn(mock(StoreHandler.class));
        when(context.supplyGuard(anyLong())).thenReturn(guard);
        when(context.writeBuffer()).thenReturn(new UnsafeBufferEx(new byte[8192]));

        return new McpBindingConfig(config, new McpConfiguration(), context);
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

    @Test
    public void shouldUseSharedAuthorizationWhenRouteHasNoOverride()
    {
        GuardHandler guard = mock(GuardHandler.class);

        RouteConfig noOverride = rawRoute(100L, null);
        McpBindingConfig binding = binding(guard, "{shared}", List.of(noOverride));
        binding.cache.authorization = 3L;

        long authorization = binding.routeCacheAuthorization(0L, 100L);

        assertThat(authorization, equalTo(3L));
        verify(guard, times(0)).reauthorize(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    public void shouldUseRouteOverrideAuthorizationWhenConfigured()
    {
        GuardHandler guard = mock(GuardHandler.class);
        when(guard.reauthorize(anyLong(), anyLong(), anyLong(), eq("{shared}"))).thenReturn(3L);
        when(guard.reauthorize(anyLong(), anyLong(), anyLong(), eq("{override}"))).thenReturn(9L);

        RouteConfig overridden = rawRoute(200L, "{override}");
        McpBindingConfig binding = binding(guard, "{shared}", List.of(overridden));

        long authorization = binding.routeCacheAuthorization(0L, 200L);

        assertThat(authorization, equalTo(9L));
        verify(guard, times(1)).reauthorize(anyLong(), anyLong(), anyLong(), eq("{override}"));
        verify(guard, times(0)).reauthorize(anyLong(), anyLong(), anyLong(), eq("{shared}"));
    }
}
