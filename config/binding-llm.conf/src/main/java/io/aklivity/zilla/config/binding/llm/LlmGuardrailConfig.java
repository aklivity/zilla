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

public final class LlmGuardrailConfig extends Config
{
    public final String name;
    public final List<LlmGuardrailStageConfig> request;
    public final List<LlmGuardrailStageConfig> response;

    public static LlmGuardrailConfigBuilder<LlmGuardrailConfig> builder()
    {
        return new LlmGuardrailConfigBuilder<>(identity());
    }

    public static <T> LlmGuardrailConfigBuilder<T> builder(
        Function<LlmGuardrailConfig, T> mapper)
    {
        return new LlmGuardrailConfigBuilder<>(mapper);
    }

    LlmGuardrailConfig(
        String name,
        List<LlmGuardrailStageConfig> request,
        List<LlmGuardrailStageConfig> response)
    {
        this.name = name;
        this.request = request;
        this.response = response;
    }
}
