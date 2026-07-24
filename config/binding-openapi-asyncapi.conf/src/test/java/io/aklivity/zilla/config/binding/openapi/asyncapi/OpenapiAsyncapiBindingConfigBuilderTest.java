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
package io.aklivity.zilla.config.binding.openapi.asyncapi;

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import java.util.Set;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;

public class OpenapiAsyncapiBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        OpenapiAsyncapiBindingConfig binding = OpenapiAsyncapiBindingConfig.builder()
            .namespace("test")
            .name("openapi_asyncapi0")
            .kind(PROXY)
            .options()
                .specs()
                    .openapi(Set.of(new OpenapiSpecificationConfig("test", null)))
                    .asyncapi(Set.of(AsyncapiSpecificationConfig.builder()
                        .label("test")
                        .build()))
                    .build()
                .build()
            .route()
                .when()
                    .operation("test")
                    .build()
                .with()
                    .operation("test")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("openapi_asyncapi0"));
        assertThat(binding.type, equalTo(OpenapiAsyncapiBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(PROXY));

        OpenapiAsyncapiOptionsConfig options = (OpenapiAsyncapiOptionsConfig) binding.options;
        assertThat(options.specs.openapi, hasSize(1));
        assertThat(options.specs.openapi.iterator().next().label, equalTo("test"));
        assertThat(options.specs.asyncapi, hasSize(1));
        assertThat(options.specs.asyncapi.iterator().next().label, equalTo("test"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(OpenapiAsyncapiConditionConfig.class));
        assertThat(((OpenapiAsyncapiConditionConfig) condition).operation, equalTo("test"));

        assertThat(route.with, instanceOf(OpenapiAsyncapiWithConfig.class));
        assertThat(((OpenapiAsyncapiWithConfig) route.with).operation, equalTo("test"));
    }
}
