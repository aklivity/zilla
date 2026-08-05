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
package io.aklivity.zilla.config.binding.http;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class HttpPatternConfigBuilder<T> extends ConfigBuilder<T, HttpPatternConfigBuilder<T>>
{
    private final Function<HttpPatternConfig, T> mapper;

    private String name;
    private String pattern;

    HttpPatternConfigBuilder(
        Function<HttpPatternConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpPatternConfigBuilder<T>> thisType()
    {
        return (Class<HttpPatternConfigBuilder<T>>) getClass();
    }

    public HttpPatternConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public HttpPatternConfigBuilder<T> pattern(
        String pattern)
    {
        this.pattern = pattern;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpPatternConfig(name, pattern));
    }
}
