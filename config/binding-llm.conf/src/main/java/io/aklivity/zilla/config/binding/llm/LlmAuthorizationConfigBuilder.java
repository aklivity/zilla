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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class LlmAuthorizationConfigBuilder<T> extends ConfigBuilder<T, LlmAuthorizationConfigBuilder<T>>
{
    private final Function<LlmAuthorizationConfig, T> mapper;

    private String name;
    private String credentials;

    LlmAuthorizationConfigBuilder(
        Function<LlmAuthorizationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmAuthorizationConfigBuilder<T>> thisType()
    {
        return (Class<LlmAuthorizationConfigBuilder<T>>) getClass();
    }

    public LlmAuthorizationConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public LlmAuthorizationConfigBuilder<T> credentials(
        String credentials)
    {
        this.credentials = credentials;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new LlmAuthorizationConfig(name, credentials));
    }
}
