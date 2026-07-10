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

import java.nio.charset.StandardCharsets;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * Builds the synthetic {@code Tool} JSON descriptors advertised in {@code tools/list} for the three
 * agent-callable search-family tools ({@code search_tools}, {@code describe_tool}, {@code execute_tool}),
 * once per binding lifecycle (never re-serialized per request).
 */
public final class McpSearchToolDescriptor
{
    private static final String SEARCH_TOOLS_DESCRIPTION =
        "Search the tool catalog by relevance and return references to matching tools.";
    private static final String QUERY_DESCRIPTION = "Natural-language search query";
    private static final String LIMIT_DESCRIPTION = "Maximum number of results to return";

    private static final String DESCRIBE_TOOL_DESCRIPTION =
        "Look up the full definition (including input/output schema) of a specific tool discovered via " +
        McpToolNames.SEARCH_TOOLS + ".";
    private static final String NAME_DESCRIPTION = "Exact tool name, as returned by " + McpToolNames.SEARCH_TOOLS;

    private static final String EXECUTE_TOOL_DESCRIPTION =
        "Invoke a specific tool by name, exactly as tools/call would, once its schema is known via " +
        McpToolNames.DESCRIBE_TOOL + ".";
    private static final String ARGUMENTS_DESCRIPTION = "Arguments to pass to the named tool";

    private McpSearchToolDescriptor()
    {
    }

    public static byte[] buildSearchTools(
        String toolkit)
    {
        JsonObject inputSchema = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder()
                .add("query", Json.createObjectBuilder()
                    .add("type", "string")
                    .add("description", QUERY_DESCRIPTION))
                .add("max_results", Json.createObjectBuilder()
                    .add("type", "integer")
                    .add("description", LIMIT_DESCRIPTION)))
            .add("required", Json.createArrayBuilder().add("query"))
            .build();

        return build(McpToolNames.effectiveName(toolkit, McpToolNames.SEARCH_TOOLS), SEARCH_TOOLS_DESCRIPTION, inputSchema);
    }

    public static byte[] buildDescribeTool(
        String toolkit)
    {
        JsonObject inputSchema = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder()
                .add("name", Json.createObjectBuilder()
                    .add("type", "string")
                    .add("description", NAME_DESCRIPTION)))
            .add("required", Json.createArrayBuilder().add("name"))
            .build();

        return build(McpToolNames.effectiveName(toolkit, McpToolNames.DESCRIBE_TOOL), DESCRIBE_TOOL_DESCRIPTION, inputSchema);
    }

    public static byte[] buildExecuteTool(
        String toolkit)
    {
        JsonObject inputSchema = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder()
                .add("name", Json.createObjectBuilder()
                    .add("type", "string")
                    .add("description", NAME_DESCRIPTION))
                .add("arguments", Json.createObjectBuilder()
                    .add("type", "object")
                    .add("description", ARGUMENTS_DESCRIPTION)))
            .add("required", Json.createArrayBuilder().add("name"))
            .build();

        return build(McpToolNames.effectiveName(toolkit, McpToolNames.EXECUTE_TOOL), EXECUTE_TOOL_DESCRIPTION, inputSchema);
    }

    private static byte[] build(
        String name,
        String description,
        JsonObject inputSchema)
    {
        JsonObject descriptor = Json.createObjectBuilder()
            .add("name", name)
            .add("description", description)
            .add("inputSchema", inputSchema)
            .build();

        return descriptor.toString().getBytes(StandardCharsets.UTF_8);
    }
}
