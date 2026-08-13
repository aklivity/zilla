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

public final class FileSystemSecretEntryConfig extends Config
{
    public final String active;
    public final Map<String, String> versions;
    public final String algorithm;

    public static FileSystemSecretEntryConfigBuilder<FileSystemSecretEntryConfig> builder()
    {
        return new FileSystemSecretEntryConfigBuilder<>(FileSystemSecretEntryConfig.class::cast);
    }

    public static <T> FileSystemSecretEntryConfigBuilder<T> builder(
        Function<FileSystemSecretEntryConfig, T> mapper)
    {
        return new FileSystemSecretEntryConfigBuilder<>(mapper);
    }

    FileSystemSecretEntryConfig(
        String active,
        Map<String, String> versions,
        String algorithm)
    {
        this.active = active;
        this.versions = versions;
        this.algorithm = algorithm;
    }
}
