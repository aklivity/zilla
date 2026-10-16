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
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolAnnotationsConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolConfig;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;

public class McpGrpcToolsListTest
{
    private static final String PROTO =
        "syntax = \"proto3\";\n" +
        "package example.sql.v1;\n" +
        "service SQLService {\n" +
        "  rpc Query(QueryRequest) returns (QueryResult);\n" +
        "  rpc Execute(QueryRequest) returns (QueryResult);\n" +
        "  rpc Watch(QueryRequest) returns (stream QueryResult);\n" +
        "}\n" +
        "message QueryRequest { string statement = 1; }\n" +
        "message QueryResult { string status = 1; }\n";

    private static final String INPUT = "{\"type\":\"object\",\"properties\":{\"statement\":{\"type\":\"string\"}}}";
    private static final String OUTPUT = "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\"}}}";

    private final ProtobufSchema schema = Protobuf.schema(PROTO);

    @Test
    public void shouldListToolsWithoutOutputSchemaByDefault()
    {
        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, null, null);

        String json = new String(McpGrpcToolsList.toJson(registry.tools()), StandardCharsets.UTF_8);

        assertThat(json, equalTo(
            "{\"tools\":[" +
                "{\"name\":\"example_sql_v1_sql_service_query\"," +
                    "\"description\":\"example.sql.v1.SQLService/Query\"," +
                    "\"inputSchema\":" + INPUT + "," +
                    "\"annotations\":{\"openWorldHint\":false}}," +
                "{\"name\":\"example_sql_v1_sql_service_execute\"," +
                    "\"description\":\"example.sql.v1.SQLService/Execute\"," +
                    "\"inputSchema\":" + INPUT + "," +
                    "\"annotations\":{\"openWorldHint\":false}}]}"));
    }

    @Test
    public void shouldListToolsWithTitleDescriptionAnnotationsAndOutputSchema()
    {
        List<McpGrpcToolConfig> overrides = List.of(
            McpGrpcToolConfig.builder()
                .name("example_sql_v1_sql_service_query")
                .title("Query SQL")
                .description("Run a read-only SQL query")
                .summary("Ran SQL query")
                .annotations(McpGrpcToolAnnotationsConfig.builder().readOnlyHint(true).idempotentHint(true).build())
                .build(),
            McpGrpcToolConfig.builder()
                .name("example_sql_v1_sql_service_execute")
                .title("Execute SQL")
                .annotations(McpGrpcToolAnnotationsConfig.builder().destructiveHint(true).build())
                .build());

        McpGrpcToolRegistry registry = McpGrpcToolRegistry.of(schema, overrides, null);

        String json = new String(McpGrpcToolsList.toJson(registry.tools()), StandardCharsets.UTF_8);

        assertThat(json, equalTo(
            "{\"tools\":[" +
                "{\"name\":\"example_sql_v1_sql_service_query\",\"title\":\"Query SQL\"," +
                    "\"description\":\"Run a read-only SQL query\"," +
                    "\"inputSchema\":" + INPUT + ",\"outputSchema\":" + OUTPUT + "," +
                    "\"annotations\":{\"readOnlyHint\":true,\"idempotentHint\":true,\"openWorldHint\":false}}," +
                "{\"name\":\"example_sql_v1_sql_service_execute\",\"title\":\"Execute SQL\"," +
                    "\"description\":\"example.sql.v1.SQLService/Execute\"," +
                    "\"inputSchema\":" + INPUT + "," +
                    "\"annotations\":{\"destructiveHint\":true,\"openWorldHint\":false}}]}"));
    }

    @Test
    public void shouldListNoToolsAsEmptyArray()
    {
        String json = new String(McpGrpcToolsList.toJson(List.of()), StandardCharsets.UTF_8);

        assertThat(json, equalTo("{\"tools\":[]}"));
    }
}
