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

public final class FileSystemVaultConfig extends VaultConfig
{
    public static FileSystemVaultConfigBuilder<FileSystemVaultConfig> builder()
    {
        return new FileSystemVaultConfigBuilder<>(FileSystemVaultConfig.class::cast);
    }

    public static <T> FileSystemVaultConfigBuilder<T> builder(
        Function<VaultConfig, T> mapper)
    {
        return new FileSystemVaultConfigBuilder<>(mapper);
    }

    FileSystemVaultConfig(
        String namespace,
        String name,
        String type,
        OptionsConfig options)
    {
        super(namespace, name, type, options);
    }
}
