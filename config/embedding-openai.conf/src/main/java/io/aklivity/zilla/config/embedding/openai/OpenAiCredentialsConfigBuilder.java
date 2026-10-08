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
package io.aklivity.zilla.config.embedding.openai;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class OpenAiCredentialsConfigBuilder<T> extends ConfigBuilder<T, OpenAiCredentialsConfigBuilder<T>>
{
    private final Function<OpenAiCredentialsConfig, T> mapper;

    private String apiKey;

    OpenAiCredentialsConfigBuilder(
        Function<OpenAiCredentialsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenAiCredentialsConfigBuilder<T>> thisType()
    {
        return (Class<OpenAiCredentialsConfigBuilder<T>>) getClass();
    }

    public OpenAiCredentialsConfigBuilder<T> apiKey(
        String apiKey)
    {
        this.apiKey = apiKey;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new OpenAiCredentialsConfig(apiKey));
    }
}
