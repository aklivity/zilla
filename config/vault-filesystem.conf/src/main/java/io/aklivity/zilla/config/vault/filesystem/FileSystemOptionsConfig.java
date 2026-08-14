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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class FileSystemOptionsConfig extends OptionsConfig
{
    public final FileSystemStoreConfig keys;
    public final FileSystemStoreConfig trust;
    public final FileSystemStoreConfig signers;
    public final FileSystemSecretsConfig secrets;
    public final String revocation;

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
        FileSystemStoreConfig signers,
        FileSystemSecretsConfig secrets,
        String revocation)
    {
        super(List.of(), resolveResources(keys, trust, secrets));
        this.keys = keys;
        this.trust = trust;
        this.signers = signers;
        this.secrets = secrets;
        this.revocation = revocation;
    }

    private static List<String> resolveResources(
        FileSystemStoreConfig keys,
        FileSystemStoreConfig trust,
        FileSystemSecretsConfig secrets)
    {
        List<String> resources = new LinkedList<>();
        if (keys != null && keys.store != null)
        {
            resources.add(keys.store);
        }
        if (trust != null && trust.store != null)
        {
            resources.add(trust.store);
        }
        if (secrets != null && secrets.store != null)
        {
            resources.add(secrets.store);
        }
        return resources;
    }
}
