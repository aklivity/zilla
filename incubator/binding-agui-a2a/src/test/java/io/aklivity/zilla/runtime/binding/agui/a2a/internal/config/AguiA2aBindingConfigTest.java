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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aConditionConfig;
import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aWithConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.GenericRouteConfigBuilder;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public class AguiA2aBindingConfigTest
{
    private static RouteConfig route(
        int order,
        String agent,
        String rewrite,
        boolean authorized)
    {
        GenericRouteConfigBuilder<RouteConfig> builder = RouteConfig.builder().order(order);
        if (agent != null)
        {
            builder = builder.when(AguiA2aConditionConfig.builder().agent(agent).build());
        }
        if (rewrite != null)
        {
            builder = builder.with(AguiA2aWithConfig.builder().agent(rewrite).build());
        }
        RouteConfig config = builder.exit("a2a0").build();
        config.authorized = (auth, identity) -> authorized;
        return config;
    }

    private static AguiA2aBindingConfig binding(
        List<RouteConfig> routes)
    {
        BindingConfig config = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("agui-a2a")
            .kind(KindConfig.PROXY)
            .routes(routes)
            .build();
        config.resolveId = name -> 1L;
        return new AguiA2aBindingConfig(config);
    }

    @Test
    public void shouldResolveRouteByAgent()
    {
        AguiA2aBindingConfig binding = binding(List.of(route(0, "agent0", null, true), route(1, "agent1", null, true)));

        assertThat(binding.routes, hasSize(2));
        assertThat(binding.resolveRoute(0L, "agent1").agent, equalTo("agent1"));
        assertThat(binding.resolveRoute(0L, "agent2"), nullValue());
    }

    @Test
    public void shouldResolveCatchAllRoute()
    {
        AguiA2aBindingConfig binding = binding(List.of(route(0, null, null, true)));

        assertThat(binding.resolveRoute(0L, "agent9").agent, nullValue());
    }

    @Test
    public void shouldSkipUnauthorizedRoute()
    {
        AguiA2aBindingConfig binding = binding(List.of(route(0, "agent0", null, false), route(1, null, null, true)));

        assertThat(binding.resolveRoute(0L, "agent0").agent, nullValue());
    }

    @Test
    public void shouldPassThroughOrRewriteAgent()
    {
        AguiA2aBindingConfig binding = binding(List.of(route(0, "agent0", "agent1", true), route(1, null, null, true)));

        assertThat(binding.resolveRoute(0L, "agent0").resolveAgent("agent0"), equalTo("agent1"));
        assertThat(binding.resolveRoute(0L, "agent5").resolveAgent("agent5"), equalTo("agent5"));
    }
}
