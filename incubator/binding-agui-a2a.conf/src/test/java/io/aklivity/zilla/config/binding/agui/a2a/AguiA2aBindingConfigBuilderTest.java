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
package io.aklivity.zilla.config.binding.agui.a2a;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.config.engine.KindConfig;

public class AguiA2aBindingConfigBuilderTest
{
    @Test
    public void shouldBuildProxyBindingWithRoutes()
    {
        AguiA2aBindingConfig binding = AguiA2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .route()
                .exit("a2a0")
                .guarded()
                    .name("test0")
                    .role("agent")
                    .build()
                .when()
                    .agent("agent0")
                    .build()
                .with()
                    .agent("agent1")
                    .identity("${guarded['test0'].identity}")
                    .build()
                .build()
            .build();

        assertThat(binding.type, equalTo("agui-a2a"));
        assertThat(binding.kind, equalTo(KindConfig.PROXY));
        assertThat(binding.options, nullValue());
        assertThat(binding.routes, hasSize(1));
        assertThat(binding.routes.get(0).exit, equalTo("a2a0"));
        assertThat(binding.routes.get(0).guarded, hasSize(1));
        assertThat(binding.routes.get(0).when.get(0), instanceOf(AguiA2aConditionConfig.class));
        assertThat(((AguiA2aConditionConfig) binding.routes.get(0).when.get(0)).agent, equalTo("agent0"));
        assertThat(((AguiA2aWithConfig) binding.routes.get(0).with).agent, equalTo("agent1"));
        assertThat(((AguiA2aWithConfig) binding.routes.get(0).with).identity, equalTo("${guarded['test0'].identity}"));
    }
}
