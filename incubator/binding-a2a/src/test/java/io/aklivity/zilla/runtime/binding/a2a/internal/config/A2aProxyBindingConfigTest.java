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
package io.aklivity.zilla.runtime.binding.a2a.internal.config;

import static io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyResolution.Outcome.AGENT_NOT_FOUND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyResolution.Outcome.AGENT_REQUIRED;
import static io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyResolution.Outcome.FORBIDDEN;
import static io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyResolution.Outcome.ROUTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aBindingConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class A2aProxyBindingConfigTest
{
    private static final long AUTHORIZED = 1L;

    private final EngineContext context = mock(EngineContext.class);
    private final GuardHandler guard = mock(GuardHandler.class);

    @Test
    public void shouldRouteByExactAgent()
    {
        final A2aProxyResolution resolution = routes(false).resolve("weather", AUTHORIZED);

        assertThat(resolution.outcome, equalTo(ROUTED));
        assertThat(resolution.route.id, equalTo(11L));
    }

    @Test
    public void shouldRouteByAgentList()
    {
        assertThat(routes(false).resolve("travel", AUTHORIZED).route.id, equalTo(12L));
        assertThat(routes(false).resolve("flights", AUTHORIZED).route.id, equalTo(12L));
    }

    @Test
    public void shouldRouteUnmatchedAgentToDefaultRoute()
    {
        final A2aProxyResolution resolution = routes(true).resolve("unknown", AUTHORIZED);

        assertThat(resolution.outcome, equalTo(ROUTED));
        assertThat(resolution.route.id, equalTo(13L));
    }

    @Test
    public void shouldRouteMissingAgentToDefaultRoute()
    {
        assertThat(routes(true).resolve(null, AUTHORIZED).route.id, equalTo(13L));
    }

    @Test
    public void shouldRejectMissingAgentWithoutDefaultRoute()
    {
        assertThat(routes(false).resolve(null, AUTHORIZED).outcome, equalTo(AGENT_REQUIRED));
    }

    @Test
    public void shouldRejectUnknownAgentWithoutDefaultRoute()
    {
        assertThat(routes(false).resolve("unknown", AUTHORIZED).outcome, equalTo(AGENT_NOT_FOUND));
    }

    @Test
    public void shouldRejectUnauthorizedAgentWithRequiredRoles()
    {
        final A2aProxyResolution resolution = routes(false).resolve("travel", 0L);

        assertThat(resolution.outcome, equalTo(FORBIDDEN));
        assertThat(resolution.roles, contains("agents:travel"));
        assertThat(resolution.route, nullValue());
    }

    @Test
    public void shouldVerifyWithGuardOfGuardedRoute()
    {
        when(context.supplyGuard(7L)).thenReturn(guard);

        assertThat(routes(false).guard, sameInstance(guard));
    }

    @Test
    public void shouldVerifyWithCacheAuthorizationGuard()
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .options()
                .cache()
                    .store("memory0")
                    .authorization()
                        .name("test0")
                        .credentials("token")
                        .build()
                    .build()
                .build()
            .route()
                .exit("app1")
                .when()
                    .agent(List.of("weather"))
                    .build()
                .build()
            .build();
        config.resolveId = name -> 9L;
        config.routes.get(0).authorized = (authorization, identity) -> true;
        when(context.supplyGuard(9L)).thenReturn(guard);

        assertThat(new A2aProxyBindingConfig(config, context).guard, sameInstance(guard));
    }

    @Test
    public void shouldNotVerifyWithoutGuard()
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .route()
                .exit("app1")
                .build()
            .build();

        config.resolveId = name -> 0L;

        assertThat(new A2aProxyBindingConfig(config, context).guard, nullValue());
    }

    private A2aProxyBindingConfig routes(
        boolean defaultRoute)
    {
        final var builder = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .route()
                .exit("app1")
                .when()
                    .agent(List.of("weather"))
                    .build()
                .build()
            .route()
                .exit("app2")
                .when()
                    .agent(List.of("travel", "flights"))
                    .build()
                .guarded()
                    .name("test0")
                    .role("agents:travel")
                    .build()
                .build();

        final BindingConfig config = (defaultRoute
            ? builder.route()
                .exit("app3")
                .build()
            : builder)
            .build();

        config.resolveId = name -> 0L;
        config.routes.get(0).id = 11L;
        config.routes.get(0).authorized = (authorization, identity) -> true;
        config.routes.get(1).id = 12L;
        config.routes.get(1).guarded.get(0).id = 7L;
        config.routes.get(1).authorized = (authorization, identity) -> authorization == AUTHORIZED;

        if (defaultRoute)
        {
            config.routes.get(2).id = 13L;
            config.routes.get(2).authorized = (authorization, identity) -> true;
        }

        return new A2aProxyBindingConfig(config, context);
    }
}
