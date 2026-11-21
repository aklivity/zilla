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

import org.junit.Test;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;

public class AguiRouteConfigBuilderTest
{
    @Test
    public void shouldBuildClientRouteWithAgentAndPath()
    {
        BindingConfig binding = AguiBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.CLIENT)
            .options()
                .server("http://localhost:8080/agent")
                .build()
            .route()
                .exit("net1")
                .when()
                    .agent("agent-1")
                    .build()
                .with()
                    .path("/routed")
                    .build()
                .build()
            .build();

        AguiConditionConfig when = (AguiConditionConfig) binding.routes.get(0).when.get(0);
        AguiWithConfig with = (AguiWithConfig) binding.routes.get(0).with;

        assertThat(when.agent, equalTo("agent-1"));
        assertThat(with.path, equalTo("/routed"));
    }

    @Test
    public void shouldBuildServerRouteWithPathAndAgent()
    {
        BindingConfig binding = AguiBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.SERVER)
            .route()
                .exit("app1")
                .when()
                    .path("/agents/{name}")
                    .build()
                .with()
                    .agent("${params.name}")
                    .build()
                .build()
            .build();

        AguiConditionConfig when = (AguiConditionConfig) binding.routes.get(0).when.get(0);
        AguiWithConfig with = (AguiWithConfig) binding.routes.get(0).with;

        assertThat(when.path, equalTo("/agents/{name}"));
        assertThat(with.agent, equalTo("${params.name}"));
    }
}
