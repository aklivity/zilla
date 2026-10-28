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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class A2aBindingConfigTest
{
    private final EngineContext context = mock(EngineContext.class);

    @Test
    public void shouldDeriveBasePathFromUrl()
    {
        final A2aBindingConfig binding = new A2aBindingConfig(server(A2aOptionsConfig.builder()
            .url("http://localhost:8080/a2a/")
            .build()), context);

        assertThat(binding.url, equalTo("http://localhost:8080/a2a/"));
        assertThat(binding.basePath, equalTo("/a2a"));
        assertThat(binding.secured(), equalTo(false));
    }

    @Test
    public void shouldDefaultWithoutOptions()
    {
        final A2aBindingConfig binding = new A2aBindingConfig(server(null), context);

        assertThat(binding.url, nullValue());
        assertThat(binding.basePath, nullValue());
        assertThat(binding.pushPath, nullValue());
        assertThat(binding.isPushPath("/a2a/push/c-1"), equalTo(false));
        assertThat(binding.dialects.resolve("1.0", "SendMessage"), notNullValue());
        assertThat(binding.dialects.resolve("0.3", "message/send"), notNullValue());
    }

    @Test
    public void shouldRestrictVersions()
    {
        final A2aBindingConfig binding = new A2aBindingConfig(server(A2aOptionsConfig.builder()
            .versions(List.of("1.0"))
            .build()), context);

        assertThat(binding.dialects.resolve("1.0", "SendMessage"), notNullValue());
        assertThat(binding.dialects.resolve("0.3", "message/send"), nullValue());
    }

    @Test
    public void shouldMatchPushPath()
    {
        final A2aBindingConfig binding = new A2aBindingConfig(server(A2aOptionsConfig.builder()
            .serverPush()
                .url("http://localhost:8080/a2a/push")
                .path("/a2a/push/")
                .build()
            .build()), context);

        assertThat(binding.pushPath, equalTo("/a2a/push"));
        assertThat(binding.isPushPath("/a2a/push/sealed-1"), equalTo(true));
        assertThat(binding.isPushPath("/a2a/push"), equalTo(false));
        assertThat(binding.isPushPath(null), equalTo(false));
        assertThat(binding.pushConfigId("/a2a/push/sealed-1"), equalTo("sealed-1"));
    }

    @Test
    public void shouldResolveGuard()
    {
        final GuardHandler guard = mock(GuardHandler.class);
        when(context.supplyGuard(anyLong())).thenReturn(guard);

        final A2aBindingConfig binding = new A2aBindingConfig(server(A2aOptionsConfig.builder()
            .authorization()
                .name("test0")
                .credentials("Bearer {credentials}")
                .build()
            .build()), context);

        assertThat(binding.guard, equalTo(guard));
        assertThat(binding.secured(), equalTo(true));
    }

    @Test
    public void shouldRecognizeCardPaths()
    {
        assertThat(A2aBindingConfig.isCardPath("/.well-known/agent-card.json"), equalTo(true));
        assertThat(A2aBindingConfig.isCardPath("/a2a/weather/.well-known/agent.json"), equalTo(true));
        assertThat(A2aBindingConfig.isCardPath("/a2a/weather"), equalTo(false));
        assertThat(A2aBindingConfig.isCardPath(null), equalTo(false));
        assertThat(A2aBindingConfig.isLegacyCardPath("/.well-known/agent.json"), equalTo(true));
        assertThat(A2aBindingConfig.isLegacyCardPath("/.well-known/agent-card.json"), equalTo(false));
        assertThat(A2aBindingConfig.isLegacyCardPath(null), equalTo(false));
        assertThat(A2aBindingConfig.cardPrefix("/a2a/weather/.well-known/agent-card.json"), equalTo("/a2a/weather"));
        assertThat(A2aBindingConfig.cardPrefix("/.well-known/agent.json"), equalTo(""));
    }

    @Test
    public void shouldResolveAuthorizedRoute()
    {
        final BindingConfig config = server(null);
        final A2aBindingConfig binding = new A2aBindingConfig(config, context);

        assertThat(binding.resolve(0L).id, equalTo(1L));
        assertThat(binding.resolve(1L), nullValue());
    }

    private static BindingConfig server(
        A2aOptionsConfig options)
    {
        final RouteConfig route = RouteConfig.builder()
            .exit("app0")
            .build();
        route.id = 1L;
        route.authorized = (authorization, identity) -> authorization == 0L;

        final BindingConfig binding = io.aklivity.zilla.config.binding.a2a.A2aBindingConfig.builder()
            .namespace("test")
            .name("net0")
            .kind(KindConfig.SERVER)
            .options(options)
            .route(route)
            .build();
        binding.id = 2L;
        binding.resolveId = name -> 3L;
        return binding;
    }
}
