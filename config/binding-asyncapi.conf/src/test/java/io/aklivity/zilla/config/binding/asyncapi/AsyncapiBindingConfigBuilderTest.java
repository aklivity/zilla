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
package io.aklivity.zilla.config.binding.asyncapi;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;

public class AsyncapiBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        AsyncapiBindingConfig binding = AsyncapiBindingConfig.builder()
            .namespace("test")
            .name("asyncapi0")
            .kind(SERVER)
            .options()
                .spec()
                    .label("test")
                    .build()
                .build()
            .route()
                .when()
                    .operation("test")
                    .build()
                .with()
                    .spec("test")
                    .operation("test")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("asyncapi0"));
        assertThat(binding.type, equalTo(AsyncapiBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        AsyncapiOptionsConfig options = (AsyncapiOptionsConfig) binding.options;
        assertThat(options.specs, hasSize(1));

        AsyncapiSpecificationConfig spec = options.specs.get(0);
        assertThat(spec.label, equalTo("test"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(AsyncapiConditionConfig.class));
        assertThat(((AsyncapiConditionConfig) condition).operation, equalTo("test"));

        WithConfig with = route.with;
        assertThat(with, instanceOf(AsyncapiWithConfig.class));
        assertThat(((AsyncapiWithConfig) with).spec, equalTo("test"));
        assertThat(((AsyncapiWithConfig) with).operation, equalTo("test"));
    }
}
