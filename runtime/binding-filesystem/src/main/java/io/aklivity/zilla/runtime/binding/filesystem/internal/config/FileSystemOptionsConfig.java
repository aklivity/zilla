/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.filesystem.internal.config;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class FileSystemOptionsConfig extends OptionsConfig
{
    public final URI location;
    public final FileSystemSymbolicLinksConfig symlinks;

    private transient FileSystem fileSystem;

    public FileSystemOptionsConfig(
        URI location,
        FileSystemSymbolicLinksConfig symlinks)
    {
        this.location = location;
        this.symlinks = symlinks;
    }

    public FileSystem fileSystem(
        URI resolvedRoot)
    {
        if (fileSystem == null)
        {
            URI uri = "file".equals(resolvedRoot.getScheme()) ? resolvedRoot.resolve("/") : resolvedRoot;
            fileSystem = FileSystems.getFileSystem(uri);
        }

        return fileSystem;
    }
}
