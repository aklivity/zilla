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
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class FileSystemOptionsConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<OptionsConfig, T> mapper;

    private FileSystemStoreConfig keys;
    private FileSystemStoreConfig trust;
    private FileSystemStoreConfig signers;

    FileSystemOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public FileSystemStoreConfigBuilder<FileSystemOptionsConfigBuilder<T>> keys()
    {
        return new FileSystemStoreConfigBuilder<>(this::keys);
    }

    public FileSystemStoreConfigBuilder<FileSystemOptionsConfigBuilder<T>> trust()
    {
        return new FileSystemStoreConfigBuilder<>(this::trust);
    }

    public FileSystemStoreConfigBuilder<FileSystemOptionsConfigBuilder<T>> signers()
    {
        return new FileSystemStoreConfigBuilder<>(this::signers);
    }

    public FileSystemOptionsConfigBuilder<T> keys(
        FileSystemStoreConfig keys)
    {
        this.keys = keys;
        return this;
    }

    public FileSystemOptionsConfigBuilder<T> trust(
        FileSystemStoreConfig trust)
    {
        this.trust = trust;
        return this;
    }

    public FileSystemOptionsConfigBuilder<T> signers(
        FileSystemStoreConfig signers)
    {
        this.signers = signers;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new FileSystemOptionsConfig(keys, trust, signers));
    }
}
