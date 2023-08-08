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
package io.aklivity.zilla.runtime.vault.filesystem.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class FileSystemStoreConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<FileSystemStoreConfig, T> mapper;

    private String store;
    private String type;
    private String password;

    FileSystemStoreConfigBuilder(
        Function<FileSystemStoreConfig, T> mapper)
    {
        this.mapper = mapper;
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

    @Override
    public T build()
    {
        return mapper.apply(new FileSystemStoreConfig(store, type, password));
    }
}
