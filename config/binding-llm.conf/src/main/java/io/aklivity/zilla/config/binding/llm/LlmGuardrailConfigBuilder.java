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

public final class LlmGuardrailConfigBuilder<T> extends ConfigBuilder<T, LlmGuardrailConfigBuilder<T>>
{
    private final Function<LlmGuardrailConfig, T> mapper;

    private String name;
    private List<LlmGuardrailStageConfig> request;
    private List<LlmGuardrailStageConfig> response;

    LlmGuardrailConfigBuilder(
        Function<LlmGuardrailConfig, T> mapper)
    {
        this.mapper = mapper;
        this.request = new ArrayList<>();
        this.response = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmGuardrailConfigBuilder<T>> thisType()
    {
        return (Class<LlmGuardrailConfigBuilder<T>>) getClass();
    }

    public LlmGuardrailConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public LlmGuardrailConfigBuilder<T> request(
        LlmGuardrailStageConfig stage)
    {
        this.request.add(stage);
        return this;
    }

    public LlmGuardrailStageConfigBuilder<LlmGuardrailConfigBuilder<T>> request()
    {
        return LlmGuardrailStageConfig.builder(this::request);
    }

    public LlmGuardrailConfigBuilder<T> response(
        LlmGuardrailStageConfig stage)
    {
        this.response.add(stage);
        return this;
    }

    public LlmGuardrailStageConfigBuilder<LlmGuardrailConfigBuilder<T>> response()
    {
        return LlmGuardrailStageConfig.builder(this::response);
    }

    @Override
    public T build()
    {
        return mapper.apply(new LlmGuardrailConfig(name, List.copyOf(request), List.copyOf(response)));
    }
}
