/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpExposeConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<HttpExposeConfig, T> mapper;

    private Set<String> headers;

    public HttpExposeConfigBuilder(
        Function<HttpExposeConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpExposeConfigBuilder<T> header(
        String header)
    {
        if (headers == null)
        {
            headers = new LinkedHashSet<>();
        }
        headers.add(header);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpExposeConfig(headers));
    }
}
