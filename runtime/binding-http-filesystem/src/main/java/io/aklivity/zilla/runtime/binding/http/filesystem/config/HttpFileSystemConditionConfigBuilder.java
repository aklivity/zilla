/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.filesystem.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpFileSystemConditionConfigBuilder<T> extends ConfigBuilder<T, HttpFileSystemConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String path;
    private String method;

    HttpFileSystemConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpFileSystemConditionConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    public HttpFileSystemConditionConfigBuilder<T> method(
        String method)
    {
        this.method = method;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpFileSystemConditionConfigBuilder<T>> thisType()
    {
        return (Class<HttpFileSystemConditionConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpFileSystemConditionConfig(path, method));
    }
}
