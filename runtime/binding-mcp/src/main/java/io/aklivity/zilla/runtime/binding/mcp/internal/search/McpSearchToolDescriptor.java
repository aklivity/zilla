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
 * Builds the synthetic {@code Tool} JSON descriptor advertised in {@code tools/list} for the
 * agent-callable search tool, once per binding lifecycle (never re-serialized per request).
 */
public final class McpSearchToolDescriptor
{
    private static final String DESCRIPTION =
        "Search the tool catalog by relevance and return references to matching tools.";
    private static final String QUERY_DESCRIPTION = "Natural-language search query";
    private static final String LIMIT_DESCRIPTION = "Maximum number of results to return";

    private McpSearchToolDescriptor()
    {
    }

    public static byte[] build(
        String tool)
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

        JsonObject descriptor = Json.createObjectBuilder()
            .add("name", tool)
            .add("description", DESCRIPTION)
            .add("inputSchema", inputSchema)
            .build();

        return descriptor.toString().getBytes(StandardCharsets.UTF_8);
    }
}
