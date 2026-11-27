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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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

public class A2aClientBindingConfigTest
{
    private static final long AUTHORIZED = 1L;

    private final EngineContext context = mock(EngineContext.class);
    private final GuardHandler guard = mock(GuardHandler.class);

    @Test
    public void shouldDefaultOptions()
    {
        final A2aClientBindingConfig binding = new A2aClientBindingConfig(bare(), context);

        assertThat(binding.versions, contains("1.0", "0.3"));
        assertThat(binding.guard, nullValue());
        assertThat(binding.serverScheme, nullValue());
        assertThat(binding.serverAuthority, nullValue());
        assertThat(binding.serverPath, nullValue());
        assertThat(binding.authorizationOf("token-1"), equalTo("Bearer token-1"));
        assertThat(binding.authorizationOf(null), nullValue());
    }

    @Test
    public void shouldDefaultPush()
    {
        final A2aClientBindingConfig binding = new A2aClientBindingConfig(bare(), context);

        assertThat(binding.pushMaxLength, equalTo(-1));
        assertThat(binding.pushPolicy.permits("https://client.example.com/hooks/1"), equalTo(true));
        assertThat(binding.pushPolicy.permits("http://127.0.0.1/hooks/1"), equalTo(false));
    }

    @Test
    public void shouldResolvePush()
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .options()
                .clientPush()
                    .allow(List.of("http://10.0.0.5/hooks/*"))
                    .maxLength(4096)
                    .build()
                .build()
            .build();
        config.resolveId = name -> 0L;

        final A2aClientBindingConfig binding = new A2aClientBindingConfig(config, context);

        assertThat(binding.pushMaxLength, equalTo(4096));
        assertThat(binding.pushPolicy.permits("http://10.0.0.5/hooks/1"), equalTo(true));
        assertThat(binding.pushPolicy.permits("https://client.example.com/hooks/1"), equalTo(false));
    }

    @Test
    public void shouldResolveServer()
    {
        assertThat(server("http://localhost:8080/a2a").serverAuthority, equalTo("localhost:8080"));
        assertThat(server("http://localhost:8080/a2a").serverScheme, equalTo("http"));
        assertThat(server("http://localhost:8080/a2a").serverPath, equalTo("/a2a"));
        assertThat(server("https://weather.example.com").serverAuthority, equalTo("weather.example.com:443"));
        assertThat(server("https://weather.example.com").serverPath, equalTo(""));
        assertThat(server("http://weather.example.com/").serverAuthority, equalTo("weather.example.com:80"));
        assertThat(server("http://weather.example.com/a2a/").serverPath, equalTo("/a2a"));
    }

    @Test
    public void shouldAuthorizeWithStaticCredentials()
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .options()
                .server("http://localhost:8080/a2a")
                .authorization()
                    .name("test0")
                    .credentials("Bearer static-token")
                    .build()
                .build()
            .build();
        config.id = 1L;
        config.resolveId = name -> 7L;
        when(context.supplyGuard(7L)).thenReturn(guard);

        final A2aClientBindingConfig binding = new A2aClientBindingConfig(config, context);

        assertThat(binding.guard, sameInstance(guard));
        assertThat(binding.authorizationOf(null), equalTo("Bearer static-token"));
        assertThat(binding.authorizationOf("ignored"), equalTo("Bearer static-token"));
    }

    @Test
    public void shouldResolveFirstAuthorizedAgentRoute()
    {
        final A2aClientBindingConfig binding = guarded();

        assertThat(binding.resolve(AUTHORIZED).id, equalTo(12L));
        assertThat(binding.resolve(0L).id, equalTo(12L));
    }

    @Test
    public void shouldNotResolveWhenNoRouteAuthorized()
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .route()
                .exit("net0")
                .guarded()
                    .name("test0")
                    .role("agents:all")
                    .build()
                .build()
            .build();
        config.routes.get(0).authorized = (authorization, identity) -> authorization == AUTHORIZED;

        final A2aClientBindingConfig binding = new A2aClientBindingConfig(config, context);

        assertThat(binding.resolve(0L), nullValue());
        assertThat(binding.resolve(AUTHORIZED), org.hamcrest.Matchers.notNullValue());
    }

    @Test
    public void shouldDescribeRolesOfSkills()
    {
        final A2aClientBindingConfig binding = guarded();

        assertThat(binding.rolesOf("forecast"), contains("agents:forecast"));
        assertThat(binding.rolesOf("weather"), empty());
    }

    @Test
    public void shouldDenyWhenEverySkillRestrictedAndNoneSatisfied()
    {
        final A2aClientBindingConfig binding = guarded();

        assertThat(binding.deniedRoles(List.of("forecast"), 0L), contains("agents:forecast"));
        assertThat(binding.deniedRoles(List.of("forecast"), AUTHORIZED), empty());
    }

    @Test
    public void shouldNotDenyWhenSomeSkillUnrestricted()
    {
        final A2aClientBindingConfig binding = guarded();

        assertThat(binding.deniedRoles(List.of("forecast", "weather"), 0L), empty());
        assertThat(binding.deniedRoles(List.of(), 0L), empty());
    }

    private A2aClientBindingConfig guarded()
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .options()
                .server("http://localhost:8080/a2a")
                .build()
            .route()
                .exit("net0")
                .when()
                    .skill(List.of("forecast"))
                    .build()
                .guarded()
                    .name("test0")
                    .role("agents:forecast")
                    .build()
                .build()
            .route()
                .exit("net0")
                .build()
            .build();
        config.routes.get(0).id = 11L;
        config.routes.get(0).authorized = (authorization, identity) -> authorization == AUTHORIZED;
        config.routes.get(1).id = 12L;
        config.routes.get(1).authorized = (authorization, identity) -> true;

        return new A2aClientBindingConfig(config, context);
    }

    private A2aClientBindingConfig server(
        String server)
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .options()
                .server(server)
                .build()
            .build();

        return new A2aClientBindingConfig(config, context);
    }

    private static BindingConfig bare()
    {
        return A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .build();
    }
}
