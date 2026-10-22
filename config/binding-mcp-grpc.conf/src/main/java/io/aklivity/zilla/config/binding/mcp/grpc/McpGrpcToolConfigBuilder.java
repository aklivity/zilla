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

public final class McpGrpcToolConfigBuilder<T> extends ConfigBuilder<T, McpGrpcToolConfigBuilder<T>>
{
    private final Function<McpGrpcToolConfig, T> mapper;

    private String name;
    private String title;
    private String description;
    private String summary;
    private McpGrpcToolAnnotationsConfig annotations;

    McpGrpcToolConfigBuilder(
        Function<McpGrpcToolConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpGrpcToolConfigBuilder<T>> thisType()
    {
        return (Class<McpGrpcToolConfigBuilder<T>>) getClass();
    }

    public McpGrpcToolConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public McpGrpcToolConfigBuilder<T> title(
        String title)
    {
        this.title = title;
        return this;
    }

    public McpGrpcToolConfigBuilder<T> description(
        String description)
    {
        this.description = description;
        return this;
    }

    public McpGrpcToolConfigBuilder<T> summary(
        String summary)
    {
        this.summary = summary;
        return this;
    }

    public McpGrpcToolConfigBuilder<T> annotations(
        McpGrpcToolAnnotationsConfig annotations)
    {
        this.annotations = annotations;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpGrpcToolConfig(name, title, description, summary, annotations));
    }
}
