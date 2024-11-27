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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class HttpFileSystemWithConfigBuilder<T> extends ConfigBuilder<T, HttpFileSystemWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private String directory;
    private String path;

    HttpFileSystemWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpFileSystemWithConfigBuilder<T> directory(
        String directory)
    {
        this.directory = directory;
        return this;
    }

    public HttpFileSystemWithConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpFileSystemWithConfigBuilder<T>> thisType()
    {
        return (Class<HttpFileSystemWithConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpFileSystemWithConfig(directory, path));
    }
}
