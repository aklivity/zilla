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

import org.junit.Test;

public class McpGrpcNamesTest
{
    @Test
    public void shouldSnakeCaseWordsAndAcronyms()
    {
        assertThat(McpGrpcNames.snake("SQLService"), equalTo("sql_service"));
        assertThat(McpGrpcNames.snake("HTTPServer"), equalTo("http_server"));
        assertThat(McpGrpcNames.snake("GetURL"), equalTo("get_url"));
        assertThat(McpGrpcNames.snake("GetUrl"), equalTo("get_url"));
        assertThat(McpGrpcNames.snake("Query"), equalTo("query"));
        assertThat(McpGrpcNames.snake("Http2Server"), equalTo("http2_server"));
    }

    @Test
    public void shouldKeepSeparatorsFromPackageSegments()
    {
        assertThat(McpGrpcNames.snake("order_items"), equalTo("order_items"));
        assertThat(McpGrpcNames.snake("v1"), equalTo("v1"));
        assertThat(McpGrpcNames.snake("__a__b__"), equalTo("a_b"));
    }

    @Test
    public void shouldQualifyToolNameWithPackageServiceAndMethod()
    {
        assertThat(McpGrpcNames.toolName("example.sql.v1.SQLService", "Query"),
            equalTo("example_sql_v1_sql_service_query"));
    }

    @Test
    public void shouldQualifyToolNameWithoutPackage()
    {
        assertThat(McpGrpcNames.toolName("SQLService", "Query"), equalTo("sql_service_query"));
    }

    @Test
    public void shouldQualifyFoldedNamesIdentically()
    {
        assertThat(McpGrpcNames.toolName("acme.order_items.Cart", "Add"),
            equalTo(McpGrpcNames.toolName("acme.order.ItemsCart", "Add")));
    }

    @Test
    public void shouldAcceptValidToolNames()
    {
        assertThat(McpGrpcNames.validToolName("query"), equalTo(true));
        assertThat(McpGrpcNames.validToolName("a-b_c9"), equalTo(true));
        assertThat(McpGrpcNames.validToolName("x".repeat(64)), equalTo(true));
    }

    @Test
    public void shouldRejectInvalidToolNames()
    {
        assertThat(McpGrpcNames.validToolName(""), equalTo(false));
        assertThat(McpGrpcNames.validToolName("has space"), equalTo(false));
        assertThat(McpGrpcNames.validToolName("x".repeat(65)), equalTo(false));
    }
}
