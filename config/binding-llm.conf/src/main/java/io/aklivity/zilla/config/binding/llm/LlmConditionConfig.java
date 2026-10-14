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

public final class LlmConditionConfig extends ConditionConfig
{
    public final String dialect;
    public final List<String> model;

    LlmConditionConfig(
        String dialect,
        List<String> model)
    {
        this.dialect = dialect;
        this.model = model;
    }

    public static LlmConditionConfigBuilder<LlmConditionConfig> builder()
    {
        return new LlmConditionConfigBuilder<>(LlmConditionConfig.class::cast);
    }

    public static <T> LlmConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new LlmConditionConfigBuilder<>(mapper);
    }
}
