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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpExplicitToolsEagerConfigBuilder<T> extends
    ConfigBuilder<T, McpExplicitToolsEagerConfigBuilder<T>>
{
    private final Function<McpExplicitToolsEagerConfig, T> mapper;

    private List<String> match;

    McpExplicitToolsEagerConfigBuilder(
        Function<McpExplicitToolsEagerConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpExplicitToolsEagerConfigBuilder<T>> thisType()
    {
        return (Class<McpExplicitToolsEagerConfigBuilder<T>>) getClass();
    }

    public McpExplicitToolsEagerConfigBuilder<T> match(
        List<String> match)
    {
        this.match = match;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpExplicitToolsEagerConfig(match));
    }
}
