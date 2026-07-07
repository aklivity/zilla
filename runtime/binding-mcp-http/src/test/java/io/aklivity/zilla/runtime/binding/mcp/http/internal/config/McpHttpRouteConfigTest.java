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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class McpHttpRouteConfigTest
{
    @Test
    public void shouldResolveHeaderWhenArgumentPresent()
    {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":path", "/notifications");
        headers.put("x-trace-id", "${args.trace.id}");
        McpHttpWithConfig with = McpHttpWithConfig.builder().headers(headers).build();
        RouteConfig route = RouteConfig.builder().with(with).build();
        McpHttpRouteConfig config = new McpHttpRouteConfig(route);

        Map<String, String> args = Map.of("trace.id", "trace-abc");
        Map<String, String> resolved = config.resolveHeaders(args, Map.of());

        assertThat(resolved.get("x-trace-id"), equalTo("trace-abc"));
    }

    @Test
    public void shouldOmitHeaderWhenArgumentAbsent()
    {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":path", "/notifications");
        headers.put("x-trace-id", "${args.trace.id}");
        McpHttpWithConfig with = McpHttpWithConfig.builder().headers(headers).build();
        RouteConfig route = RouteConfig.builder().with(with).build();
        McpHttpRouteConfig config = new McpHttpRouteConfig(route);

        Map<String, String> resolved = config.resolveHeaders(Map.of(), Map.of());

        assertThat(resolved.get("x-trace-id"), nullValue());
    }

    @Test
    public void shouldCollectNestedArgAccessorFromHeader()
    {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":path", "/notifications");
        headers.put("x-trace-id", "${args.trace.id}");
        McpHttpWithConfig with = McpHttpWithConfig.builder().headers(headers).build();
        RouteConfig route = RouteConfig.builder().with(with).build();
        McpHttpRouteConfig config = new McpHttpRouteConfig(route);

        assertThat(config.argAccessors, equalTo(List.of("trace.id")));
    }

    @Test
    public void shouldAggregateCookiesWhenAllPresent()
    {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":path", "/notifications");
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("a", "${args.a}");
        cookies.put("b", "${args.b}");
        McpHttpWithConfig with = McpHttpWithConfig.builder().headers(headers).cookies(cookies).build();
        RouteConfig route = RouteConfig.builder().with(with).build();
        McpHttpRouteConfig config = new McpHttpRouteConfig(route);

        Map<String, String> args = Map.of("a", "1", "b", "2");
        String resolved = config.resolveCookies(args, Map.of());

        assertThat(resolved, equalTo("a=1; b=2"));
    }

    @Test
    public void shouldOmitOneCookieWhenPartiallyAbsent()
    {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":path", "/notifications");
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("a", "${args.a}");
        cookies.put("b", "${args.b}");
        McpHttpWithConfig with = McpHttpWithConfig.builder().headers(headers).cookies(cookies).build();
        RouteConfig route = RouteConfig.builder().with(with).build();
        McpHttpRouteConfig config = new McpHttpRouteConfig(route);

        Map<String, String> args = Map.of("a", "1");
        String resolved = config.resolveCookies(args, Map.of());

        assertThat(resolved, equalTo("a=1"));
    }

    @Test
    public void shouldOmitCookieHeaderWhenAllAbsent()
    {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":path", "/notifications");
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("a", "${args.a}");
        cookies.put("b", "${args.b}");
        McpHttpWithConfig with = McpHttpWithConfig.builder().headers(headers).cookies(cookies).build();
        RouteConfig route = RouteConfig.builder().with(with).build();
        McpHttpRouteConfig config = new McpHttpRouteConfig(route);

        String resolved = config.resolveCookies(Map.of(), Map.of());

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldCollectArgAccessorFromCookie()
    {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":path", "/notifications");
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("session", "${args.session.id}");
        McpHttpWithConfig with = McpHttpWithConfig.builder().headers(headers).cookies(cookies).build();
        RouteConfig route = RouteConfig.builder().with(with).build();
        McpHttpRouteConfig config = new McpHttpRouteConfig(route);

        assertThat(config.argAccessors, equalTo(List.of("session.id")));
    }
}
