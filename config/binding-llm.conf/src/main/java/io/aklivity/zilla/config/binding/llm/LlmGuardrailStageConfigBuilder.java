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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;

public final class LlmGuardrailStageConfigBuilder<T> extends ConfigBuilder<T, LlmGuardrailStageConfigBuilder<T>>
{
    private final Function<LlmGuardrailStageConfig, T> mapper;

    private List<String> sections;
    private ModelConfig value;
    private int overlap;

    LlmGuardrailStageConfigBuilder(
        Function<LlmGuardrailStageConfig, T> mapper)
    {
        this.mapper = mapper;
        this.sections = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmGuardrailStageConfigBuilder<T>> thisType()
    {
        return (Class<LlmGuardrailStageConfigBuilder<T>>) getClass();
    }

    public LlmGuardrailStageConfigBuilder<T> section(
        String section)
    {
        this.sections.add(section);
        return this;
    }

    public LlmGuardrailStageConfigBuilder<T> sections(
        List<String> sections)
    {
        this.sections = new ArrayList<>(sections);
        return this;
    }

    public LlmGuardrailStageConfigBuilder<T> value(
        ModelConfig value)
    {
        this.value = value;
        return this;
    }

    public LlmGuardrailStageConfigBuilder<T> overlap(
        int overlap)
    {
        this.overlap = overlap;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new LlmGuardrailStageConfig(List.copyOf(sections), value, overlap));
    }
}
