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

import io.aklivity.zilla.config.engine.WithConfig;

public final class LlmWithConfig extends WithConfig
{
    public final List<String> fallback;

    LlmWithConfig(
        List<String> fallback)
    {
        this.fallback = fallback;
    }

    public static LlmWithConfigBuilder<LlmWithConfig> builder()
    {
        return new LlmWithConfigBuilder<>(LlmWithConfig.class::cast);
    }

    public static <T> LlmWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new LlmWithConfigBuilder<>(mapper);
    }
}
