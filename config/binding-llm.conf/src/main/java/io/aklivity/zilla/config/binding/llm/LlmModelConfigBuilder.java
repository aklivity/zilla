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

import static java.util.Collections.unmodifiableMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class LlmModelConfigBuilder<T> extends ConfigBuilder<T, LlmModelConfigBuilder<T>>
{
    private final Function<LlmModelConfig, T> mapper;
    private final Map<String, Integer> ladder;

    private String name;
    private Boolean sampling;
    private String thinking;
    private Integer budgetMinimum;
    private Integer budgetMaximum;

    LlmModelConfigBuilder(
        Function<LlmModelConfig, T> mapper)
    {
        this.mapper = mapper;
        this.ladder = new LinkedHashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmModelConfigBuilder<T>> thisType()
    {
        return (Class<LlmModelConfigBuilder<T>>) getClass();
    }

    public LlmModelConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public LlmModelConfigBuilder<T> sampling(
        Boolean sampling)
    {
        this.sampling = sampling;
        return this;
    }

    public LlmModelConfigBuilder<T> thinking(
        String thinking)
    {
        this.thinking = thinking;
        return this;
    }

    public LlmModelConfigBuilder<T> budgetMinimum(
        Integer budgetMinimum)
    {
        this.budgetMinimum = budgetMinimum;
        return this;
    }

    public LlmModelConfigBuilder<T> budgetMaximum(
        Integer budgetMaximum)
    {
        this.budgetMaximum = budgetMaximum;
        return this;
    }

    public LlmModelConfigBuilder<T> ladder(
        String effort,
        int budget)
    {
        this.ladder.put(effort, budget);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new LlmModelConfig(name, sampling, thinking, budgetMinimum, budgetMaximum,
            unmodifiableMap(new LinkedHashMap<>(ladder))));
    }
}
