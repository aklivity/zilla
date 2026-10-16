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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.json.Json;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolAnnotationsConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolConfig;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;

public class McpGrpcToolRegistryTest
{
    private static final String PROTO =
        "syntax = \"proto3\";\n" +
        "package example.sql.v1;\n" +
        "service SQLService {\n" +
        "  rpc Query(QueryRequest) returns (QueryResult) { option (acme.audience) = \"readonly\"; }\n" +
        "  rpc Execute(QueryRequest) returns (QueryResult) { option (acme.audience) = \"operator\"; }\n" +
        "  rpc Watch(QueryRequest) returns (stream QueryResult);\n" +
        "  rpc Upload(stream QueryRequest) returns (QueryResult);\n" +
        "}\n" +
        "message QueryRequest { string statement = 1; }\n" +
        "message QueryResult { string status = 1; }\n";

    private final ProtobufSchema schema = Protobuf.schema(PROTO);

    @Test
    public void shouldMapEveryUnaryMethodWhenNoRoutes()
    {
        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, null);

        assertThat(names(registry), contains(
            "example_sql_v1_sql_service_query",
            "example_sql_v1_sql_service_execute"));
        assertThat(registry.denied(), empty());
    }

    @Test
    public void shouldDescribeToolFromMethod()
    {
        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, null);

        McpGrpcTool tool = registry.tool("example_sql_v1_sql_service_query");

        assertThat(tool.service, equalTo("example.sql.v1.SQLService"));
        assertThat(tool.method, equalTo("Query"));
        assertThat(tool.inputType, equalTo("example.sql.v1.QueryRequest"));
        assertThat(tool.outputType, equalTo("example.sql.v1.QueryResult"));
        assertThat(tool.title, nullValue());
        assertThat(tool.description, equalTo("example.sql.v1.SQLService/Query"));
        assertThat(tool.summary, nullValue());
        assertThat(tool.annotations.openWorldHint, equalTo(false));
        assertThat(tool.annotations.readOnlyHint, nullValue());
        assertThat(tool.inputSchema.toString(),
            equalTo("{\"type\":\"object\",\"properties\":{\"statement\":{\"type\":\"string\"}}}"));
        assertThat(tool.outputSchema, nullValue());
    }

    @Test
    public void shouldPinNameOnExplicitRoute()
    {
        List<McpGrpcRouteConfig> routes = List.of(
            new McpGrpcRouteConfig("query", "example.sql.v1.SQLService/Query", null),
            new McpGrpcRouteConfig(null, "example.sql.v1.SQLService/*", null));

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, routes);

        assertThat(names(registry), contains("query", "example_sql_v1_sql_service_execute"));
    }

    @Test
    public void shouldSelectByMethodGlob()
    {
        List<McpGrpcRouteConfig> routes = List.of(
            new McpGrpcRouteConfig(null, "example.sql.v1.SQLService/Ex*", null));

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, routes);

        assertThat(names(registry), contains("example_sql_v1_sql_service_execute"));
    }

    @Test
    public void shouldSelectByOption()
    {
        List<McpGrpcRouteConfig> routes = List.of(
            new McpGrpcRouteConfig(null, null, Map.of("acme.audience", Json.createValue("readonly"))));

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, routes);

        assertThat(names(registry), contains("example_sql_v1_sql_service_query"));
    }

    @Test
    public void shouldNotSelectMethodWhenOptionValueDiffers()
    {
        List<McpGrpcRouteConfig> routes = List.of(
            new McpGrpcRouteConfig(null, null, Map.of("acme.audience", Json.createValue("nobody"))));

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, routes);

        assertThat(registry.tools(), empty());
    }

    @Test
    public void shouldClaimMethodForFirstMatchingRouteOnly()
    {
        List<McpGrpcRouteConfig> routes = List.of(
            new McpGrpcRouteConfig("run", "example.sql.v1.SQLService/Execute", null),
            new McpGrpcRouteConfig(null, "example.sql.v1.SQLService/*", null),
            new McpGrpcRouteConfig("again", "example.sql.v1.SQLService/Execute", null));

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, routes);

        assertThat(names(registry), contains("run", "example_sql_v1_sql_service_query"));
    }

    @Test
    public void shouldNeverMapStreamingMethodsEvenWhenNamedExplicitly()
    {
        List<McpGrpcRouteConfig> routes = List.of(
            new McpGrpcRouteConfig("watch", "example.sql.v1.SQLService/Watch", null));

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, routes);

        assertThat(registry.tools(), empty());
    }

    @Test
    public void shouldApplyToolOverrides()
    {
        List<McpGrpcToolConfig> overrides = List.of(
            McpGrpcToolConfig.builder()
                .name("example_sql_v1_sql_service_query")
                .title("Query SQL")
                .description("Run a read-only SQL query")
                .summary("Ran SQL query")
                .annotations(McpGrpcToolAnnotationsConfig.builder().readOnlyHint(true).idempotentHint(true).build())
                .build());

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, overrides, null);

        McpGrpcTool tool = registry.tool("example_sql_v1_sql_service_query");
        assertThat(tool.title, equalTo("Query SQL"));
        assertThat(tool.description, equalTo("Run a read-only SQL query"));
        assertThat(tool.summary, equalTo("Ran SQL query"));
        assertThat(tool.annotations.readOnlyHint, equalTo(true));
        assertThat(tool.annotations.idempotentHint, equalTo(true));
        assertThat(tool.annotations.destructiveHint, nullValue());
        assertThat(tool.annotations.openWorldHint, equalTo(false));
        assertThat(tool.outputSchema.toString(),
            equalTo("{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\"}}}"));
    }

    @Test
    public void shouldLetConfiguredOpenWorldHintOverrideDefault()
    {
        List<McpGrpcToolConfig> overrides = List.of(
            McpGrpcToolConfig.builder()
                .name("example_sql_v1_sql_service_query")
                .annotations(McpGrpcToolAnnotationsConfig.builder().openWorldHint(true).build())
                .build());

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, overrides, null);

        assertThat(registry.tool("example_sql_v1_sql_service_query").annotations.openWorldHint, equalTo(true));
    }

    @Test
    public void shouldApplyToolOverrideToPinnedName()
    {
        List<McpGrpcRouteConfig> routes = List.of(
            new McpGrpcRouteConfig("query", "example.sql.v1.SQLService/Query", null));
        List<McpGrpcToolConfig> overrides = List.of(
            McpGrpcToolConfig.builder().name("query").title("Query SQL").build());

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, overrides, routes);

        assertThat(registry.tool("query").title, equalTo("Query SQL"));
    }

    @Test
    public void shouldDenyMethodsWhoseDefaultNamesCollide()
    {
        ProtobufSchema folded = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package test;\n" +
            "service Svc { rpc GetURL(Req) returns (Res); rpc GetUrl(Req) returns (Res); rpc Other(Req) returns (Res); }\n" +
            "message Req { string id = 1; }\n" +
            "message Res { string id = 1; }\n");

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(folded, null, null);

        assertThat(names(registry), contains("test_svc_other"));
        assertThat(registry.denied(), hasSize(2));
    }

    @Test
    public void shouldDenyMethodWhoseDefaultNameExceedsLimit()
    {
        ProtobufSchema longName = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package a.very.long.package.name.for.testing.limits.of.tool.names;\n" +
            "service SomeExtremelyLongServiceName { rpc SomeExtremelyLongMethodName(Req) returns (Res); }\n" +
            "message Req { string id = 1; }\n" +
            "message Res { string id = 1; }\n");

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(longName, null, null);

        assertThat(registry.tools(), empty());
        assertThat(registry.denied(), hasSize(1));
    }

    @Test
    public void shouldDenyPinnedNameThatCollides()
    {
        List<McpGrpcRouteConfig> routes = List.of(
            new McpGrpcRouteConfig("same", "example.sql.v1.SQLService/Query", null),
            new McpGrpcRouteConfig("same", "example.sql.v1.SQLService/Execute", null));

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, routes);

        assertThat(registry.tools(), empty());
        assertThat(registry.denied(), hasSize(2));
    }

    @Test
    public void shouldCarryRouteOnTool()
    {
        McpGrpcRouteConfig route = new McpGrpcRouteConfig(null, "example.sql.v1.SQLService/Query", null);

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, List.of(route));

        assertThat(registry.tool("example_sql_v1_sql_service_query").route == route, equalTo(true));
    }

    private static List<String> names(
        McpGrpcToolRegistry registry)
    {
        return registry.tools().stream().map(tool -> tool.name).collect(Collectors.toList());
    }
}
