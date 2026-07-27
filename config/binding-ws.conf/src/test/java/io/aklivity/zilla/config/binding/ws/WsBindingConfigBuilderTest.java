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
package io.aklivity.zilla.config.binding.ws;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public class WsBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        WsBindingConfig binding = WsBindingConfig.builder()
            .namespace("test")
            .name("ws0")
            .kind(SERVER)
            .options()
                .protocol("echo")
                .scheme("http")
                .authority("localhost:8080")
                .path("/")
                .build()
            .route()
                .when()
                    .path("/echo")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("ws0"));
        assertThat(binding.type, equalTo(WsBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        WsOptionsConfig options = (WsOptionsConfig) binding.options;
        assertThat(options.protocol, equalTo("echo"));
        assertThat(options.scheme, equalTo("http"));
        assertThat(options.authority, equalTo("localhost:8080"));
        assertThat(options.path, equalTo("/"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(WsConditionConfig.class));
        assertThat(((WsConditionConfig) condition).path, equalTo("/echo"));
    }
}
