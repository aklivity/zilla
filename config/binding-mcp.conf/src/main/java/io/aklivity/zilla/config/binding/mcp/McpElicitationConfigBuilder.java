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

import java.time.Duration;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class McpElicitationConfigBuilder<T> extends ConfigBuilder<T, McpElicitationConfigBuilder<T>>
{
    private final Function<McpElicitationConfig, T> mapper;

    private String callback;
    private Duration timeout;

    public McpElicitationConfigBuilder(
        Function<McpElicitationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public McpElicitationConfigBuilder<T> callback(
        String callback)
    {
        this.callback = callback;
        return this;
    }

    public McpElicitationConfigBuilder<T> timeout(
        Duration timeout)
    {
        this.timeout = timeout;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpElicitationConfigBuilder<T>> thisType()
    {
        return (Class<McpElicitationConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        String resolved = callback != null ? callback : McpElicitationConfig.DEFAULT_CALLBACK_PATH;
        return mapper.apply(new McpElicitationConfig(resolved, timeout));
    }
}
