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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.VaultConfigBuilder;

public final class FileSystemVaultConfigBuilder<T> extends VaultConfigBuilder<T, FileSystemVaultConfigBuilder<T>>
{
    FileSystemVaultConfigBuilder(
        Function<VaultConfig, T> mapper)
    {
        super(mapper);
        type(FileSystemVaultInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<FileSystemVaultConfigBuilder<T>> thisType()
    {
        return (Class<FileSystemVaultConfigBuilder<T>>) getClass();
    }

    public FileSystemOptionsConfigBuilder<FileSystemVaultConfigBuilder<T>> options()
    {
        return new FileSystemOptionsConfigBuilder<>(this::options);
    }

    @Override
    protected VaultConfig newVault(
        String namespace,
        String name,
        String type,
        OptionsConfig options)
    {
        return new FileSystemVaultConfig(namespace, name, type, options);
    }
}
