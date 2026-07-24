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
package io.aklivity.zilla.config.binding.filesystem;

import java.net.URI;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class FileSystemOptionsConfigBuilder<T> extends ConfigBuilder<T, FileSystemOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private URI location;
    private FileSystemSymbolicLinksConfig symlinks;

    FileSystemOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<FileSystemOptionsConfigBuilder<T>> thisType()
    {
        return (Class<FileSystemOptionsConfigBuilder<T>>) getClass();
    }

    public FileSystemOptionsConfigBuilder<T> location(
        URI location)
    {
        this.location = location;
        return this;
    }

    public FileSystemOptionsConfigBuilder<T> symlinks(
        FileSystemSymbolicLinksConfig symlinks)
    {
        this.symlinks = symlinks;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new FileSystemOptionsConfig(location, symlinks));
    }
}
