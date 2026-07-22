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
package io.aklivity.zilla.config.vault.filesystem;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class FileSystemStoreConfigBuilder<T> extends ConfigBuilder<T, FileSystemStoreConfigBuilder<T>>
{
    private final Function<FileSystemStoreConfig, T> mapper;

    private String store;
    private String type;
    private String password;
    private List<String> entries;

    FileSystemStoreConfigBuilder(
        Function<FileSystemStoreConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<FileSystemStoreConfigBuilder<T>> thisType()
    {
        return (Class<FileSystemStoreConfigBuilder<T>>) getClass();
    }

    public FileSystemStoreConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public FileSystemStoreConfigBuilder<T> type(
        String type)
    {
        this.type = type;
        return this;
    }

    public FileSystemStoreConfigBuilder<T> password(
        String password)
    {
        this.password = password;
        return this;
    }

    public FileSystemStoreConfigBuilder<T> entries(
        List<String> entries)
    {
        this.entries = entries;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new FileSystemStoreConfig(store, type, password, entries));
    }
}
