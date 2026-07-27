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
package io.aklivity.zilla.config.binding.mcp;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.internal.McpBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class McpBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        McpBindingConfig binding = McpBindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .kind(SERVER)
            .options()
                .server("test")
                .build()
            .route()
                .when()
                    .tools(List.of("test"))
                    .build()
                .with()
                    .cache()
                        .credentials("test")
                        .build()
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("mcp0"));
        assertThat(binding.type, equalTo(McpBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        McpOptionsConfig options = (McpOptionsConfig) binding.options;
        assertThat(options.server, equalTo("test"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(McpConditionConfig.class));
        assertThat(((McpConditionConfig) condition).tools, equalTo(List.of("test")));

        WithConfig with = route.with;
        assertThat(with, instanceOf(McpWithConfig.class));
        assertThat(((McpWithConfig) with).cache.credentials, equalTo("test"));
    }
}
