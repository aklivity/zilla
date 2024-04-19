/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.filesystem.internal.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class FilesystemSchemaConfigBuilder<T> extends ConfigBuilder<T, FilesystemSchemaConfigBuilder<T>>
{
    private final Function<FilesystemSchemaConfig, T> mapper;

    private String subject;
    private String path;

    public FilesystemSchemaConfigBuilder(
        Function<FilesystemSchemaConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<FilesystemSchemaConfigBuilder<T>> thisType()
    {
        return (Class<FilesystemSchemaConfigBuilder<T>>) getClass();
    }

    public FilesystemSchemaConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public FilesystemSchemaConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new FilesystemSchemaConfig(subject, path));
    }
}
