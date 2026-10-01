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

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class LlmConditionConfigBuilder<T> extends ConfigBuilder<T, LlmConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String dialect;
    private List<String> model;

    LlmConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmConditionConfigBuilder<T>> thisType()
    {
        return (Class<LlmConditionConfigBuilder<T>>) getClass();
    }

    public LlmConditionConfigBuilder<T> dialect(
        String dialect)
    {
        this.dialect = dialect;
        return this;
    }

    public LlmConditionConfigBuilder<T> model(
        List<String> model)
    {
        this.model = model;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new LlmConditionConfig(dialect, model));
    }
}
