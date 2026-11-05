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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolAnnotationsConfig;

public final class McpGrpcToolsList
{
    public static byte[] toJson(
        List<McpGrpcTool> tools)
    {
        final JsonArrayBuilder array = Json.createArrayBuilder();

        for (McpGrpcTool tool : tools)
        {
            final JsonObjectBuilder item = Json.createObjectBuilder().add("name", tool.name);

            if (tool.title != null)
            {
                item.add("title", tool.title);
            }

            item.add("description", tool.description);
            item.add("inputSchema", tool.inputSchema);

            if (tool.outputSchema != null)
            {
                item.add("outputSchema", tool.outputSchema);
            }

            item.add("annotations", annotations(tool.annotations));

            array.add(item);
        }

        return Json.createObjectBuilder().add("tools", array).build().toString().getBytes(UTF_8);
    }

    private static JsonObjectBuilder annotations(
        McpGrpcToolAnnotationsConfig annotations)
    {
        final JsonObjectBuilder object = Json.createObjectBuilder();

        if (annotations.readOnlyHint != null)
        {
            object.add("readOnlyHint", annotations.readOnlyHint);
        }
        if (annotations.destructiveHint != null)
        {
            object.add("destructiveHint", annotations.destructiveHint);
        }
        if (annotations.idempotentHint != null)
        {
            object.add("idempotentHint", annotations.idempotentHint);
        }
        if (annotations.openWorldHint != null)
        {
            object.add("openWorldHint", annotations.openWorldHint);
        }

        return object;
    }

    private McpGrpcToolsList()
    {
    }
}
