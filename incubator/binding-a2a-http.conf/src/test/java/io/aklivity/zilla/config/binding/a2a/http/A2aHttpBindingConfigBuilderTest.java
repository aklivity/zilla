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
package io.aklivity.zilla.config.binding.a2a.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.engine.KindConfig;

public class A2aHttpBindingConfigBuilderTest
{
    @Test
    public void shouldBuildProxyBindingWithOptionsAndRoutes()
    {
        A2aHttpBindingConfig binding = A2aHttpBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .options()
                .embedding("embedding0")
                .store("store0")
                .threshold(0.95)
                .skill()
                    .id("get_item")
                    .description("Look up an item by id")
                    .example("get item 42")
                    .tag("items")
                    .build()
                .build()
            .route()
                .exit("http0")
                .when()
                    .skill("get_item")
                    .build()
                .with()
                    .header(":method", "GET")
                    .build()
                .build()
            .build();

        assertThat(binding.type, equalTo("a2a-http"));
        assertThat(binding.kind, equalTo(KindConfig.PROXY));

        A2aHttpOptionsConfig options = (A2aHttpOptionsConfig) binding.options;
        assertThat(options.embedding, equalTo("embedding0"));
        assertThat(options.store, equalTo("store0"));
        assertThat(options.threshold, equalTo(0.95));
        assertThat(options.skills, hasSize(1));
        assertThat(options.skills.get(0).name, equalTo("get_item"));

        assertThat(binding.routes, hasSize(1));
        assertThat(binding.routes.get(0).exit, equalTo("http0"));
        assertThat(binding.routes.get(0).when.get(0), instanceOf(A2aHttpConditionConfig.class));
        assertThat(((A2aHttpConditionConfig) binding.routes.get(0).when.get(0)).skill, equalTo("get_item"));
        assertThat(((A2aHttpWithConfig) binding.routes.get(0).with).headers.get(":method"), equalTo("GET"));
    }
}
