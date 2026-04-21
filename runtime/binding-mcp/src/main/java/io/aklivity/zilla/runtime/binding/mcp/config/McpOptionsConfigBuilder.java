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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class McpOptionsConfigBuilder<T> extends ConfigBuilder<T, McpOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private List<McpPromptConfig> prompts;

    public McpOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public McpOptionsConfigBuilder<T> prompt(
        String name,
        String description)
    {
        if (prompts == null)
        {
            prompts = new ArrayList<>();
        }
        prompts.add(new McpPromptConfig(name, description));
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpOptionsConfigBuilder<T>> thisType()
    {
        return (Class<McpOptionsConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpOptionsConfig(prompts));
    }
}
