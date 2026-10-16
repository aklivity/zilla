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
package io.aklivity.zilla.config.binding.mcp.grpc;

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import jakarta.json.Json;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.grpc.internal.McpGrpcBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class McpGrpcBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        McpGrpcBindingConfig binding = McpGrpcBindingConfig.builder()
            .namespace("test")
            .name("mcp-grpc0")
            .kind(CLIENT)
            .options()
                .server("http://localhost:8080")
                .catalog()
                    .name("catalog0")
                    .subject("sql")
                    .version("latest")
                    .build()
                .tool()
                    .name("example_sql_v1_sql_service_query")
                    .summary("Ran SQL query")
                    .build()
                .build()
            .route()
                .when()
                    .tool("query")
                    .build()
                .with()
                    .method("example.sql.v1.SQLService/Query")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("mcp-grpc0"));
        assertThat(binding.type, equalTo(McpGrpcBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(CLIENT));

        McpGrpcOptionsConfig options = (McpGrpcOptionsConfig) binding.options;
        assertThat(options.server, equalTo("http://localhost:8080"));
        assertThat(options.catalog.name, equalTo("catalog0"));
        assertThat(options.catalog.subject, equalTo("sql"));
        assertThat(options.catalog.version, equalTo("latest"));
        assertThat(options.tools, hasSize(1));
        assertThat(options.tools.get(0).name, equalTo("example_sql_v1_sql_service_query"));
        assertThat(options.tools.get(0).summary, equalTo("Ran SQL query"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(McpGrpcConditionConfig.class));
        assertThat(((McpGrpcConditionConfig) condition).tool, equalTo("query"));

        WithConfig with = route.with;
        assertThat(with, instanceOf(McpGrpcWithConfig.class));
        assertThat(((McpGrpcWithConfig) with).method, equalTo("example.sql.v1.SQLService/Query"));
    }

    @Test
    public void shouldBuildRouteWithOption()
    {
        McpGrpcBindingConfig binding = McpGrpcBindingConfig.builder()
            .namespace("test")
            .name("mcp-grpc0")
            .kind(CLIENT)
            .route()
                .with()
                    .option("acme.audience", Json.createValue("readonly"))
                    .build()
                .build()
            .build();

        McpGrpcWithConfig with = (McpGrpcWithConfig) binding.routes.get(0).with;
        assertThat(with.method, equalTo(null));
        assertThat(with.option.get("acme.audience"), equalTo(Json.createValue("readonly")));
    }
}
