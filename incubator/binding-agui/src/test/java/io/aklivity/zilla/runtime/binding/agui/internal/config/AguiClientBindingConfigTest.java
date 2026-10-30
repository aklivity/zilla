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
package io.aklivity.zilla.runtime.binding.agui.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.config.binding.agui.AguiBindingConfig;
import io.aklivity.zilla.config.binding.agui.AguiOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;

public class AguiClientBindingConfigTest
{
    @Test
    public void shouldResolveServerWithPort()
    {
        final AguiClientBindingConfig config = config("http://localhost:8080/agent");

        assertThat(config.serverScheme, equalTo("http"));
        assertThat(config.serverAuthority, equalTo("localhost:8080"));
        assertThat(config.serverPath, equalTo("/agent"));
    }

    @Test
    public void shouldResolveServerWithDefaultHttpPort()
    {
        final AguiClientBindingConfig config = config("http://agents.example.com/agent/run");

        assertThat(config.serverScheme, equalTo("http"));
        assertThat(config.serverAuthority, equalTo("agents.example.com:80"));
        assertThat(config.serverPath, equalTo("/agent/run"));
    }

    @Test
    public void shouldResolveServerWithDefaultHttpsPort()
    {
        final AguiClientBindingConfig config = config("https://agents.example.com/agent");

        assertThat(config.serverScheme, equalTo("https"));
        assertThat(config.serverAuthority, equalTo("agents.example.com:443"));
    }

    @Test
    public void shouldResolveServerWithoutPath()
    {
        final AguiClientBindingConfig config = config("http://localhost:8080");

        assertThat(config.serverPath, equalTo("/"));
    }

    @Test
    public void shouldNotInjectCredentialsWithoutGuard()
    {
        final AguiClientBindingConfig config = config("http://localhost:8080/agent");

        assertThat(config.guard, nullValue());
        assertThat(config.authorizationOf(1L), nullValue());
    }

    @Test
    public void shouldResolveRouteByAgent()
    {
        final AguiClientBindingConfig config = routed();

        assertThat(config.resolve("agent-0", 1L).path, equalTo("/agents/agent-0"));
        assertThat(config.resolve("agent-1", 1L).path, equalTo("/routed"));
        assertThat(config.resolve("agent-2", 1L), nullValue());
    }

    @Test
    public void shouldResolveRouteWithoutWithToServerPath()
    {
        final BindingConfig binding = AguiBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .options()
                .server("http://localhost:8080/agent")
                .build()
            .route()
                .exit("net1")
                .build()
            .build();

        binding.routes.forEach(r -> r.authorized = (a, f) -> true);
        final AguiClientBindingConfig config = new AguiClientBindingConfig(binding, null);

        assertThat(config.resolve("any", 1L).path, equalTo("/agent"));
    }

    private static AguiClientBindingConfig routed()
    {
        final BindingConfig binding = AguiBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .options()
                .server("http://localhost:8080/agent")
                .build()
            .route()
                .exit("net1")
                .when()
                    .agent("agent-0")
                    .build()
                .with()
                    .path("/agents/agent-0")
                    .build()
                .build()
            .route()
                .exit("net2")
                .when()
                    .agent("agent-1")
                    .build()
                .with()
                    .path("/routed")
                    .build()
                .build()
            .build();

        binding.routes.forEach(r -> r.authorized = (a, f) -> true);

        return new AguiClientBindingConfig(binding, null);
    }

    private static AguiClientBindingConfig config(
        String server)
    {
        final BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("agui")
            .kind(KindConfig.CLIENT)
            .options(AguiOptionsConfig.builder().server(server).build())
            .exit("net0")
            .build();

        return new AguiClientBindingConfig(binding, null);
    }
}
