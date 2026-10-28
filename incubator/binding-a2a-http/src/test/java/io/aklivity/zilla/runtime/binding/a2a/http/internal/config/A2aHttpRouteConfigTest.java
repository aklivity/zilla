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
package io.aklivity.zilla.runtime.binding.a2a.http.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpConditionConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpWithConfig;
import io.aklivity.zilla.config.engine.GenericRouteConfigBuilder;
import io.aklivity.zilla.config.engine.RouteConfig;

public class A2aHttpRouteConfigTest
{
    private static A2aHttpRouteConfig route(
        String skill,
        String path)
    {
        GenericRouteConfigBuilder<RouteConfig> builder = RouteConfig.builder();
        if (skill != null)
        {
            builder = builder.when(A2aHttpConditionConfig.builder()
                .skill(skill)
                .build());
        }
        if (path != null)
        {
            builder = builder.with(A2aHttpWithConfig::builder)
                .header(":path", path)
                .header("x-trace-id", "${args.trace.id}")
                .build();
        }
        return new A2aHttpRouteConfig(builder.exit("http0").build());
    }

    @Test
    public void shouldApplyUnscopedRouteToAnySkill()
    {
        final A2aHttpRouteConfig route = route(null, null);

        assertTrue(route.appliesToSkill("get_item"));
        assertTrue(route.appliesToSkill("create_item"));
    }

    @Test
    public void shouldApplyScopedRouteToItsOwnSkillOnly()
    {
        final A2aHttpRouteConfig route = route("get_item", null);

        assertTrue(route.appliesToSkill("get_item"));
        assertFalse(route.appliesToSkill("create_item"));
    }

    @Test
    public void shouldCollectArgumentAccessors()
    {
        final A2aHttpRouteConfig route = route("get_item", "/items/${args.id}/${args.id}");

        assertThat(route.argAccessors, equalTo(List.of("id", "trace.id")));
    }

    @Test
    public void shouldResolvePathWithEncodedArguments()
    {
        final A2aHttpRouteConfig route = route("get_item", "/items/${args.id}");

        assertThat(route.resolvePath(Map.of("id", "a b/é")), equalTo("/items/a%20b%2F%C3%A9"));
    }

    @Test
    public void shouldResolvePathWithQueryFragments()
    {
        final A2aHttpRouteConfig route = route("search_items", "/items?q=${args.q}&${?args.limit=limit}");

        assertThat(route.resolvePath(Map.of("q", "wid gets", "limit", "5")), equalTo("/items?q=wid%20gets&limit=5"));
        assertThat(route.resolvePath(Map.of("q", "widgets")), equalTo("/items?q=widgets"));
    }

    @Test
    public void shouldResolveHeadersAndOmitUnresolved()
    {
        final A2aHttpRouteConfig route = route("get_item", "/items");

        assertThat(route.resolveHeaders(Map.of("trace.id", "t-1")),
            equalTo(Map.of(":path", "/items", "x-trace-id", "t-1")));
        assertThat(route.resolveHeaders(Map.of()).get("x-trace-id"), nullValue());
    }

    @Test
    public void shouldResolveNoHeadersWithoutWith()
    {
        final A2aHttpRouteConfig route = route("get_item", null);

        assertTrue(route.resolveHeaders(Map.of()).isEmpty());
        assertThat(route.resolvePath(Map.of()), nullValue());
    }
}
