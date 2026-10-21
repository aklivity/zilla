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

import static java.util.function.Function.identity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.binding.llm.internal.LlmModelDefaults;
import io.aklivity.zilla.config.engine.Config;

public final class LlmModelConfig extends Config
{
    public final String name;
    public final Boolean sampling;
    public final String thinking;
    public final Integer budgetMinimum;
    public final Integer budgetMaximum;
    public final Map<String, Integer> ladder;

    public static LlmModelConfigBuilder<LlmModelConfig> builder()
    {
        return new LlmModelConfigBuilder<>(identity());
    }

    public static <T> LlmModelConfigBuilder<T> builder(
        Function<LlmModelConfig, T> mapper)
    {
        return new LlmModelConfigBuilder<>(mapper);
    }

    public static List<LlmModelConfig> defaults(
        String dialect)
    {
        return LlmModelDefaults.of(dialect);
    }

    LlmModelConfig(
        String name,
        Boolean sampling,
        String thinking,
        Integer budgetMinimum,
        Integer budgetMaximum,
        Map<String, Integer> ladder)
    {
        this.name = name;
        this.sampling = sampling;
        this.thinking = thinking;
        this.budgetMinimum = budgetMinimum;
        this.budgetMaximum = budgetMaximum;
        this.ladder = ladder;
    }
}
