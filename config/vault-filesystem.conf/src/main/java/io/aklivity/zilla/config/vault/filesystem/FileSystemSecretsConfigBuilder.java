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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class FileSystemSecretsConfigBuilder<T> extends ConfigBuilder<T, FileSystemSecretsConfigBuilder<T>>
{
    private final Function<FileSystemSecretsConfig, T> mapper;

    private String store;
    private String type;
    private String password;
    private Map<String, FileSystemSecretEntryConfig> entries;

    FileSystemSecretsConfigBuilder(
        Function<FileSystemSecretsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<FileSystemSecretsConfigBuilder<T>> thisType()
    {
        return (Class<FileSystemSecretsConfigBuilder<T>>) getClass();
    }

    public FileSystemSecretsConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public FileSystemSecretsConfigBuilder<T> type(
        String type)
    {
        this.type = type;
        return this;
    }

    public FileSystemSecretsConfigBuilder<T> password(
        String password)
    {
        this.password = password;
        return this;
    }

    public FileSystemSecretsConfigBuilder<T> entries(
        Map<String, FileSystemSecretEntryConfig> entries)
    {
        this.entries = entries;
        return this;
    }

    public FileSystemSecretsConfigBuilder<T> entry(
        String name,
        FileSystemSecretEntryConfig entry)
    {
        if (entries == null)
        {
            entries = new LinkedHashMap<>();
        }

        entries.put(name, entry);
        return this;
    }

    public FileSystemSecretEntryConfigBuilder<FileSystemSecretsConfigBuilder<T>> entry(
        String name)
    {
        return new FileSystemSecretEntryConfigBuilder<>(entry -> entry(name, entry));
    }

    @Override
    public T build()
    {
        return mapper.apply(new FileSystemSecretsConfig(store, type, password, entries));
    }
}
