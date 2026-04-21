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
package io.aklivity.zilla.runtime.binding.mcp.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class McpWithConfigBuilder<T> extends ConfigBuilder<T, McpWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private Map<String, String> headers;

    public McpWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public McpWithConfigBuilder<T> header(
        String name,
        String value)
    {
        if (headers == null)
        {
            headers = new LinkedHashMap<>();
        }
        headers.put(name, value);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpWithConfigBuilder<T>> thisType()
    {
        return (Class<McpWithConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpWithConfig(headers));
    }
}
