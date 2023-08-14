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

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class FileSystemOptionsConfig extends OptionsConfig
{
    public final FileSystemStoreConfig keys;
    public final FileSystemStoreConfig trust;
    public final FileSystemStoreConfig signers;

    public static FileSystemOptionsConfigBuilder<FileSystemOptionsConfig> builder()
    {
        return new FileSystemOptionsConfigBuilder<>(FileSystemOptionsConfig.class::cast);
    }

    public static <T> FileSystemOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new FileSystemOptionsConfigBuilder<>(mapper);
    }

    FileSystemOptionsConfig(
        FileSystemStoreConfig keys,
        FileSystemStoreConfig trust,
        FileSystemStoreConfig signers)
    {
        this.keys = keys;
        this.trust = trust;
        this.signers = signers;
    }
}
