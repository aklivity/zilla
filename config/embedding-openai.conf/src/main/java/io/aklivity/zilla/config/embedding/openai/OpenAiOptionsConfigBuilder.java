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
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class OpenAiOptionsConfigBuilder<T> extends ConfigBuilder<T, OpenAiOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String model;
    private OpenAiCredentialsConfig credentials;

    OpenAiOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenAiOptionsConfigBuilder<T>> thisType()
    {
        return (Class<OpenAiOptionsConfigBuilder<T>>) getClass();
    }

    public OpenAiOptionsConfigBuilder<T> model(
        String model)
    {
        this.model = model;
        return this;
    }

    public OpenAiOptionsConfigBuilder<T> credentials(
        OpenAiCredentialsConfig credentials)
    {
        this.credentials = credentials;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new OpenAiOptionsConfig(model, credentials));
    }
}
