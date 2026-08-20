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

public final class FileSystemSecretEntryConfigBuilder<T> extends ConfigBuilder<T, FileSystemSecretEntryConfigBuilder<T>>
{
    private static final String DEFAULT_VERSION = "1";

    private final Function<FileSystemSecretEntryConfig, T> mapper;

    private String active;
    private Map<String, String> versions;
    private String algorithm;

    FileSystemSecretEntryConfigBuilder(
        Function<FileSystemSecretEntryConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<FileSystemSecretEntryConfigBuilder<T>> thisType()
    {
        return (Class<FileSystemSecretEntryConfigBuilder<T>>) getClass();
    }

    public FileSystemSecretEntryConfigBuilder<T> active(
        String active)
    {
        this.active = active;
        return this;
    }

    public FileSystemSecretEntryConfigBuilder<T> versions(
        Map<String, String> versions)
    {
        this.versions = versions;
        return this;
    }

    public FileSystemSecretEntryConfigBuilder<T> version(
        String version,
        String alias)
    {
        if (versions == null)
        {
            versions = new LinkedHashMap<>();
        }

        versions.put(version, alias);
        return this;
    }

    public FileSystemSecretEntryConfigBuilder<T> algorithm(
        String algorithm)
    {
        this.algorithm = algorithm;
        return this;
    }

    public FileSystemSecretEntryConfigBuilder<T> alias(
        String alias)
    {
        return active(DEFAULT_VERSION).version(DEFAULT_VERSION, alias);
    }

    @Override
    public T build()
    {
        return mapper.apply(new FileSystemSecretEntryConfig(active, versions, algorithm));
    }
}
