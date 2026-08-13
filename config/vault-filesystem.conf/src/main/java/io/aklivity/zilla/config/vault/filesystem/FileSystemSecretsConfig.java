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

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class FileSystemSecretsConfig extends Config
{
    public final String store;
    public final String type;
    public final String password;
    public final Map<String, FileSystemSecretEntryConfig> entries;

    public static FileSystemSecretsConfigBuilder<FileSystemSecretsConfig> builder()
    {
        return new FileSystemSecretsConfigBuilder<>(FileSystemSecretsConfig.class::cast);
    }

    public static <T> FileSystemSecretsConfigBuilder<T> builder(
        Function<FileSystemSecretsConfig, T> mapper)
    {
        return new FileSystemSecretsConfigBuilder<>(mapper);
    }

    FileSystemSecretsConfig(
        String store,
        String type,
        String password,
        Map<String, FileSystemSecretEntryConfig> entries)
    {
        this.store = store;
        this.type = type;
        this.password = password;
        this.entries = entries;
    }
}
