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
package io.aklivity.zilla.config.binding.llm;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class LlmSignConfigBuilder<T> extends ConfigBuilder<T, LlmSignConfigBuilder<T>>
{
    private final Function<LlmSignConfig, T> mapper;

    private String name;
    private OptionsConfig options;

    LlmSignConfigBuilder(
        Function<LlmSignConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmSignConfigBuilder<T>> thisType()
    {
        return (Class<LlmSignConfigBuilder<T>>) getClass();
    }

    public LlmSignConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public LlmSignConfigBuilder<T> options(
        OptionsConfig options)
    {
        this.options = options;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new LlmSignConfig(name, options));
    }
}
