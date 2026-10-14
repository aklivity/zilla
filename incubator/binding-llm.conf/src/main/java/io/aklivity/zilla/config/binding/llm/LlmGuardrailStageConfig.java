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
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ModelConfig;

public final class LlmGuardrailStageConfig extends Config
{
    public final List<String> sections;
    public final ModelConfig value;

    public static LlmGuardrailStageConfigBuilder<LlmGuardrailStageConfig> builder()
    {
        return new LlmGuardrailStageConfigBuilder<>(identity());
    }

    public static <T> LlmGuardrailStageConfigBuilder<T> builder(
        Function<LlmGuardrailStageConfig, T> mapper)
    {
        return new LlmGuardrailStageConfigBuilder<>(mapper);
    }

    LlmGuardrailStageConfig(
        List<String> sections,
        ModelConfig value)
    {
        this.sections = sections;
        this.value = value;
    }
}
