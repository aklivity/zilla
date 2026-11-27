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
package io.aklivity.zilla.config.binding.agui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.engine.RouteConfig;

public class AguiRouteConfigBuilderTest
{
    @Test
    public void shouldBuildServerRoute()
    {
        RouteConfig route = new AguiRouteConfigBuilder<RouteConfig>(RouteConfig.class::cast)
            .exit("app0")
            .when()
                .path("/agents/{name}")
                .build()
            .with()
                .agent("${params.name}")
                .build()
            .build();

        assertThat(route.exit, equalTo("app0"));
        assertThat(route.when, hasSize(1));
        assertThat(route.when.get(0), instanceOf(AguiConditionConfig.class));
        assertThat(((AguiConditionConfig) route.when.get(0)).path, equalTo("/agents/{name}"));
        assertThat(((AguiWithConfig) route.with).agent, equalTo("${params.name}"));
    }

    @Test
    public void shouldBuildClientRoute()
    {
        RouteConfig route = new AguiRouteConfigBuilder<RouteConfig>(RouteConfig.class::cast)
            .exit("net0")
            .when()
                .agent("support")
                .build()
            .with()
                .path("/agents/support")
                .build()
            .build();

        assertThat(((AguiConditionConfig) route.when.get(0)).agent, equalTo("support"));
        assertThat(((AguiWithConfig) route.with).path, equalTo("/agents/support"));
    }
}
