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

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpGrpcToolAnnotationsConfigBuilder<T> extends
    ConfigBuilder<T, McpGrpcToolAnnotationsConfigBuilder<T>>
{
    private final Function<McpGrpcToolAnnotationsConfig, T> mapper;

    private Boolean readOnlyHint;
    private Boolean destructiveHint;
    private Boolean idempotentHint;
    private Boolean openWorldHint;

    McpGrpcToolAnnotationsConfigBuilder(
        Function<McpGrpcToolAnnotationsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpGrpcToolAnnotationsConfigBuilder<T>> thisType()
    {
        return (Class<McpGrpcToolAnnotationsConfigBuilder<T>>) getClass();
    }

    public McpGrpcToolAnnotationsConfigBuilder<T> readOnlyHint(
        Boolean readOnlyHint)
    {
        this.readOnlyHint = readOnlyHint;
        return this;
    }

    public McpGrpcToolAnnotationsConfigBuilder<T> destructiveHint(
        Boolean destructiveHint)
    {
        this.destructiveHint = destructiveHint;
        return this;
    }

    public McpGrpcToolAnnotationsConfigBuilder<T> idempotentHint(
        Boolean idempotentHint)
    {
        this.idempotentHint = idempotentHint;
        return this;
    }

    public McpGrpcToolAnnotationsConfigBuilder<T> openWorldHint(
        Boolean openWorldHint)
    {
        this.openWorldHint = openWorldHint;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpGrpcToolAnnotationsConfig(
            readOnlyHint, destructiveHint, idempotentHint, openWorldHint));
    }
}
