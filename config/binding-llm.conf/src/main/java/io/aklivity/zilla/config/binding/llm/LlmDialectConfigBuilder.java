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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class LlmDialectConfigBuilder<T> extends ConfigBuilder<T, LlmDialectConfigBuilder<T>>
{
    private final Function<LlmDialectConfig, T> mapper;

    private String name;
    private List<String> apis;

    LlmDialectConfigBuilder(
        Function<LlmDialectConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmDialectConfigBuilder<T>> thisType()
    {
        return (Class<LlmDialectConfigBuilder<T>>) getClass();
    }

    public LlmDialectConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public LlmDialectConfigBuilder<T> apis(
        List<String> apis)
    {
        this.apis = List.copyOf(apis);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new LlmDialectConfig(name, apis));
    }
}
