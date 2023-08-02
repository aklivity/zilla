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
package io.aklivity.zilla.runtime.vault.filesystem.internal;

import java.net.URL;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

final class FileSystemContext implements VaultContext
{
    private final Function<String, URL> resolvePath;

    FileSystemContext(
        Configuration config,
        EngineContext context)
    {
        this.resolvePath = context::resolvePath;
    }

    @Override
    public FileSystemVaultHandler attach(
        VaultConfig vault)
    {
        FileSystemOptionsConfig options = (FileSystemOptionsConfig) vault.options;
        return new FileSystemVaultHandler(options, resolvePath);
    }

    @Override
    public void detach(
        VaultConfig vault)
    {
    }
}
