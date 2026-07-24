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
package io.aklivity.zilla.config.binding.mcp.openapi;

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.openapi.internal.McpOpenapiBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class McpOpenapiBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        McpOpenapiBindingConfig binding = McpOpenapiBindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .kind(CLIENT)
            .options()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .build()
                .build()
            .route()
                .when()
                    .tool("create_pr")
                    .build()
                .with()
                    .spec("openapi_github0")
                    .operation("create_pr")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("mcp_openapi0"));
        assertThat(binding.type, equalTo(McpOpenapiBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(CLIENT));

        McpOpenapiOptionsConfig options = (McpOpenapiOptionsConfig) binding.options;
        assertThat(options.specs, hasSize(1));
        assertThat(options.specs.get(0).label, equalTo("openapi_github0"));
        assertThat(options.specs.get(0).server, equalTo("https://api.github.com"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(McpOpenapiConditionConfig.class));
        assertThat(((McpOpenapiConditionConfig) condition).tool, equalTo("create_pr"));

        WithConfig with = route.with;
        assertThat(with, instanceOf(McpOpenapiWithConfig.class));
        assertThat(((McpOpenapiWithConfig) with).spec, equalTo("openapi_github0"));
        assertThat(((McpOpenapiWithConfig) with).operation, equalTo("create_pr"));
    }
}
