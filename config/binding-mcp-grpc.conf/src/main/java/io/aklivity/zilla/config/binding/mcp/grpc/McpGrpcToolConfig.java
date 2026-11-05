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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class McpGrpcToolConfig extends Config
{
    public final String name;
    public final String title;
    public final String description;
    public final String summary;
    public final McpGrpcToolAnnotationsConfig annotations;

    public static McpGrpcToolConfigBuilder<McpGrpcToolConfig> builder()
    {
        return new McpGrpcToolConfigBuilder<>(McpGrpcToolConfig.class::cast);
    }

    public static <T> McpGrpcToolConfigBuilder<T> builder(
        Function<McpGrpcToolConfig, T> mapper)
    {
        return new McpGrpcToolConfigBuilder<>(mapper);
    }

    McpGrpcToolConfig(
        String name,
        String title,
        String description,
        String summary,
        McpGrpcToolAnnotationsConfig annotations)
    {
        this.name = name;
        this.title = title;
        this.description = description;
        this.summary = summary;
        this.annotations = annotations;
    }
}
