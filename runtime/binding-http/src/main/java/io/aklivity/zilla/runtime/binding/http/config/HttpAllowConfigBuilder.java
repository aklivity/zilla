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

public final class HttpAllowConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<HttpAllowConfig, T> mapper;

    private Set<String> origins;
    private Set<String> methods;
    private Set<String> headers;
    private boolean credentials;

    HttpAllowConfigBuilder(
        Function<HttpAllowConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpAllowConfigBuilder<T>  origin(
        String origin)
    {
        if (origins == null)
        {
            origins = new LinkedHashSet<>();
        }
        origins.add(origin);
        return this;
    }

    public HttpAllowConfigBuilder<T>  method(
        String method)
    {
        if (methods == null)
        {
            methods = new LinkedHashSet<>();
        }
        methods.add(method);
        return this;
    }

    public HttpAllowConfigBuilder<T>  header(
        String header)
    {
        if (headers == null)
        {
            headers = new LinkedHashSet<>();
        }
        headers.add(header);
        return this;
    }

    public HttpAllowConfigBuilder<T> credentials(
        boolean credentials)
    {
        this.credentials = credentials;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpAllowConfig(origins, methods, headers, credentials));
    }
}
