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
package io.aklivity.zilla.config.guard.inline;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class InlineOptionsConfigBuilder<T> extends ConfigBuilder<T, InlineOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String identity;
    private String credentials;
    private String format;

    InlineOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<InlineOptionsConfigBuilder<T>> thisType()
    {
        return (Class<InlineOptionsConfigBuilder<T>>) getClass();
    }

    public InlineOptionsConfigBuilder<T> identity(
        String identity)
    {
        this.identity = identity;
        return this;
    }

    public InlineOptionsConfigBuilder<T> credentials(
        String credentials)
    {
        this.credentials = credentials;
        return this;
    }

    public InlineOptionsConfigBuilder<T> format(
        String format)
    {
        this.format = format;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new InlineOptionsConfig(identity, credentials, format));
    }
}
