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
package io.aklivity.zilla.config.binding.mcp;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpAllToolsEagerConfigBuilder<T> extends
    ConfigBuilder<T, McpAllToolsEagerConfigBuilder<T>>
{
    private final Function<McpAllToolsEagerConfig, T> mapper;

    McpAllToolsEagerConfigBuilder(
        Function<McpAllToolsEagerConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpAllToolsEagerConfigBuilder<T>> thisType()
    {
        return (Class<McpAllToolsEagerConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpAllToolsEagerConfig());
    }
}
