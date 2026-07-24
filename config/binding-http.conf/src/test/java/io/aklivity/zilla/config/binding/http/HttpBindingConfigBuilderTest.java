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
package io.aklivity.zilla.config.binding.http;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class HttpBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        HttpBindingConfig binding = HttpBindingConfig.builder()
            .namespace("test")
            .name("http0")
            .kind(SERVER)
            .options()
                .version(HttpVersion.HTTP_1_1)
                .build()
            .route()
                .when()
                    .header("method", "GET")
                    .build()
                .with()
                    .override("content-type", "text/plain")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("http0"));
        assertThat(binding.type, equalTo(HttpBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        HttpOptionsConfig options = (HttpOptionsConfig) binding.options;
        assertThat(options.versions, hasSize(1));
        assertThat(options.versions.first(), equalTo(HttpVersion.HTTP_1_1));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(HttpConditionConfig.class));
        assertThat(((HttpConditionConfig) condition).headers.get("method"), equalTo("GET"));

        assertThat(route.with, instanceOf(HttpWithConfig.class));
        WithConfig with = route.with;
        assertThat(((HttpWithConfig) with).overrides.get("content-type"), equalTo("text/plain"));
    }
}
