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

import jakarta.json.JsonObject;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolAnnotationsConfig;

public final class McpGrpcTool
{
    public final String name;
    public final String service;
    public final String method;
    public final String inputType;
    public final String outputType;
    public final String title;
    public final String description;
    public final String summary;
    public final McpGrpcToolAnnotationsConfig annotations;
    public final JsonObject inputSchema;
    public final JsonObject outputSchema;
    public final McpGrpcRouteConfig route;

    McpGrpcTool(
        String name,
        String service,
        String method,
        String inputType,
        String outputType,
        String title,
        String description,
        String summary,
        McpGrpcToolAnnotationsConfig annotations,
        JsonObject inputSchema,
        JsonObject outputSchema,
        McpGrpcRouteConfig route)
    {
        this.name = name;
        this.service = service;
        this.method = method;
        this.inputType = inputType;
        this.outputType = outputType;
        this.title = title;
        this.description = description;
        this.summary = summary;
        this.annotations = annotations;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.route = route;
    }
}
