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
package io.aklivity.zilla.config.binding.a2a;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.KindConfig;

public class A2aBindingConfigBuilderTest
{
    @Test
    public void shouldBuildProxyBindingWithOptionsAndRoutes()
    {
        A2aBindingConfig binding = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .options()
                .cache()
                    .store("memory0")
                    .build()
                .build()
            .route()
                .exit("app1")
                .when()
                    .agent(List.of("weather"))
                    .build()
                .build()
            .build();

        assertThat(binding.type, equalTo("a2a"));
        assertThat(binding.kind, equalTo(KindConfig.PROXY));
        assertThat(((A2aOptionsConfig) binding.options).cache.store, equalTo("memory0"));
        assertThat(binding.routes, hasSize(1));
        assertThat(binding.routes.get(0).exit, equalTo("app1"));
        assertThat(binding.routes.get(0).when, hasSize(1));
        assertThat(binding.routes.get(0).when.get(0), instanceOf(A2aConditionConfig.class));
        assertThat(((A2aConditionConfig) binding.routes.get(0).when.get(0)).agent, contains("weather"));
    }
}
