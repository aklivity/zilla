/*
 * Copyright 2021-2024 Aklivity Inc
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
    public void shouldBuildToolWithConfiguredName()
    {
        byte[] bytes = McpSearchToolDescriptor.build("zilla__search_tools");

        JsonObject tool = parse(bytes);

        assertThat(tool.getString("name"), equalTo("zilla__search_tools"));
        assertThat(tool.containsKey("description"), equalTo(true));
    }

    @Test
    public void shouldBuildInputSchemaRequiringQuery()
    {
        byte[] bytes = McpSearchToolDescriptor.build("zilla__search_tools");

        JsonObject tool = parse(bytes);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");

        assertThat(inputSchema.getString("type"), equalTo("object"));
        assertThat(inputSchema.getJsonObject("properties").containsKey("query"), equalTo(true));
        assertThat(inputSchema.getJsonArray("required").getString(0), equalTo("query"));
        assertThat(inputSchema.getJsonArray("required"), hasItem(Json.createValue("query")));
    }

    private static JsonObject parse(
        byte[] bytes)
    {
        return Json.createReader(new StringReader(new String(bytes, StandardCharsets.UTF_8))).readObject();
    }
}
