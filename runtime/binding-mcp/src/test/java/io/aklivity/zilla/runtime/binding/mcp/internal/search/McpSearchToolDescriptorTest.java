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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Test;

public class McpSearchToolDescriptorTest
{
    @Test
    public void shouldBuildSearchToolsWithToolkitPrefix()
    {
        byte[] bytes = McpSearchToolDescriptor.buildSearchTools("zilla");

        JsonObject tool = parse(bytes);

        assertThat(tool.getString("name"), equalTo("zilla__search_tools"));
        assertThat(tool.containsKey("description"), equalTo(true));
    }

    @Test
    public void shouldBuildSearchToolsWithoutToolkit()
    {
        byte[] bytes = McpSearchToolDescriptor.buildSearchTools(null);

        JsonObject tool = parse(bytes);

        assertThat(tool.getString("name"), equalTo("search_tools"));
    }

    @Test
    public void shouldBuildSearchToolsInputSchemaRequiringQuery()
    {
        byte[] bytes = McpSearchToolDescriptor.buildSearchTools("zilla");

        JsonObject tool = parse(bytes);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");

        assertThat(inputSchema.getString("type"), equalTo("object"));
        assertThat(inputSchema.getJsonObject("properties").containsKey("query"), equalTo(true));
        assertThat(inputSchema.getJsonObject("properties").containsKey("max_results"), equalTo(true));
        assertThat(inputSchema.getJsonArray("required").getString(0), equalTo("query"));
        assertThat(inputSchema.getJsonArray("required"), hasItem(Json.createValue("query")));
    }

    @Test
    public void shouldBuildDescribeToolWithToolkitPrefix()
    {
        byte[] bytes = McpSearchToolDescriptor.buildDescribeTool("zilla");

        JsonObject tool = parse(bytes);

        assertThat(tool.getString("name"), equalTo("zilla__describe_tool"));
        assertThat(tool.containsKey("description"), equalTo(true));
    }

    @Test
    public void shouldBuildDescribeToolInputSchemaRequiringName()
    {
        byte[] bytes = McpSearchToolDescriptor.buildDescribeTool("zilla");

        JsonObject tool = parse(bytes);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");

        assertThat(inputSchema.getString("type"), equalTo("object"));
        assertThat(inputSchema.getJsonObject("properties").containsKey("name"), equalTo(true));
        assertThat(inputSchema.getJsonArray("required"), hasItem(Json.createValue("name")));
    }

    @Test
    public void shouldBuildExecuteToolWithToolkitPrefix()
    {
        byte[] bytes = McpSearchToolDescriptor.buildExecuteTool("zilla");

        JsonObject tool = parse(bytes);

        assertThat(tool.getString("name"), equalTo("zilla__execute_tool"));
        assertThat(tool.containsKey("description"), equalTo(true));
    }

    @Test
    public void shouldBuildExecuteToolInputSchemaWithNameRequiredAndArgumentsOptional()
    {
        byte[] bytes = McpSearchToolDescriptor.buildExecuteTool("zilla");

        JsonObject tool = parse(bytes);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");
        JsonObject properties = inputSchema.getJsonObject("properties");

        assertThat(inputSchema.getString("type"), equalTo("object"));
        assertThat(properties.containsKey("name"), equalTo(true));
        assertThat(properties.containsKey("arguments"), equalTo(true));
        assertThat(properties.getJsonObject("arguments").getString("type"), equalTo("object"));
        assertThat(inputSchema.getJsonArray("required"), hasItem(Json.createValue("name")));
        assertThat(inputSchema.getJsonArray("required").size(), equalTo(1));
    }

    private static JsonObject parse(
        byte[] bytes)
    {
        return Json.createReader(new StringReader(new String(bytes, StandardCharsets.UTF_8))).readObject();
    }
}
