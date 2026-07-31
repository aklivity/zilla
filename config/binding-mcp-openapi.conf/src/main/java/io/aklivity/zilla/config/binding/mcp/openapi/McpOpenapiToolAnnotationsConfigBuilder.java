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
package io.aklivity.zilla.config.binding.mcp.openapi;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpOpenapiToolAnnotationsConfigBuilder<T> extends
    ConfigBuilder<T, McpOpenapiToolAnnotationsConfigBuilder<T>>
{
    private final Function<McpOpenapiToolAnnotationsConfig, T> mapper;

    private String title;
    private Boolean readOnlyHint;
    private Boolean destructiveHint;
    private Boolean idempotentHint;
    private Boolean openWorldHint;

    McpOpenapiToolAnnotationsConfigBuilder(
        Function<McpOpenapiToolAnnotationsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOpenapiToolAnnotationsConfigBuilder<T>> thisType()
    {
        return (Class<McpOpenapiToolAnnotationsConfigBuilder<T>>) getClass();
    }

    public McpOpenapiToolAnnotationsConfigBuilder<T> title(
        String title)
    {
        this.title = title;
        return this;
    }

    public McpOpenapiToolAnnotationsConfigBuilder<T> readOnlyHint(
        Boolean readOnlyHint)
    {
        this.readOnlyHint = readOnlyHint;
        return this;
    }

    public McpOpenapiToolAnnotationsConfigBuilder<T> destructiveHint(
        Boolean destructiveHint)
    {
        this.destructiveHint = destructiveHint;
        return this;
    }

    public McpOpenapiToolAnnotationsConfigBuilder<T> idempotentHint(
        Boolean idempotentHint)
    {
        this.idempotentHint = idempotentHint;
        return this;
    }

    public McpOpenapiToolAnnotationsConfigBuilder<T> openWorldHint(
        Boolean openWorldHint)
    {
        this.openWorldHint = openWorldHint;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOpenapiToolAnnotationsConfig(
            title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint));
    }
}
