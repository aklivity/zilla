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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class LlmAuthorizationConfig extends Config
{
    public final String name;
    public final String credentials;

    public static LlmAuthorizationConfigBuilder<LlmAuthorizationConfig> builder()
    {
        return new LlmAuthorizationConfigBuilder<>(identity());
    }

    public static <T> LlmAuthorizationConfigBuilder<T> builder(
        Function<LlmAuthorizationConfig, T> mapper)
    {
        return new LlmAuthorizationConfigBuilder<>(mapper);
    }

    LlmAuthorizationConfig(
        String name,
        String credentials)
    {
        this.name = name;
        this.credentials = credentials;
    }
}
