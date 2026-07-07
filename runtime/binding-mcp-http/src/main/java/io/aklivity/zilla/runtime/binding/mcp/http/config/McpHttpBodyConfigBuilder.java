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
package io.aklivity.zilla.runtime.binding.mcp.http.config;

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class McpHttpBodyConfigBuilder<T> extends ConfigBuilder<T, McpHttpBodyConfigBuilder<T>>
{
    private final Function<McpHttpBodyConfig, T> mapper;

    private ModelConfig model;
    private Map<String, String> template;

    McpHttpBodyConfigBuilder(
        Function<McpHttpBodyConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpHttpBodyConfigBuilder<T>> thisType()
    {
        return (Class<McpHttpBodyConfigBuilder<T>>) getClass();
    }

    public McpHttpBodyConfigBuilder<T> model(
        ModelConfig model)
    {
        this.model = model;
        return this;
    }

    public McpHttpBodyConfigBuilder<T> template(
        Map<String, String> template)
    {
        this.template = template;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpHttpBodyConfig(model, template));
    }
}
