/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.config;

import java.util.function.Function;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpAffinityConfigBuilder<T> extends ConfigBuilder<T, HttpAffinityConfigBuilder<T>>
{
    private final Function<HttpAffinityConfig, T> mapper;

    private HttpAffinitySource source;
    private String name;
    private Pattern match;

    HttpAffinityConfigBuilder(
        Function<HttpAffinityConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpAffinityConfigBuilder<T>> thisType()
    {
        return (Class<HttpAffinityConfigBuilder<T>>) getClass();
    }

    public HttpAffinityConfigBuilder<T> header(
        String name)
    {
        this.source = HttpAffinitySource.HEADER;
        this.name = name;
        return this;
    }

    public HttpAffinityConfigBuilder<T> query(
        String name)
    {
        this.source = HttpAffinitySource.QUERY;
        this.name = name;
        return this;
    }

    public HttpAffinityConfigBuilder<T> match(
        String match)
    {
        this.match = Pattern.compile(match);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpAffinityConfig(source, name, match));
    }
}
