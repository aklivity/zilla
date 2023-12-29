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
package io.aklivity.zilla.runtime.binding.filesystem.internal;

import static io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemBinding.NAME;

import java.io.File;
import java.net.URI;

import io.aklivity.zilla.runtime.engine.Configuration;

public class FileSystemConfiguration extends Configuration
{
    private static final ConfigurationDef FILE_SYSTEM_CONFIG;

    public static final PropertyDef<URI> FILE_SYSTEM_SERVER_ROOT;

    static
    {
        final ConfigurationDef config = new ConfigurationDef(String.format("zilla.binding.%s", NAME));
        FILE_SYSTEM_SERVER_ROOT = config.property(URI.class, "server.root",
            FileSystemConfiguration::decodeServerRoot, new File(".").toURI());

        FILE_SYSTEM_CONFIG = config;
    }

    public FileSystemConfiguration(
        Configuration config)
    {
        super(FILE_SYSTEM_CONFIG, config);
    }

    public URI serverRoot()
    {
        return FILE_SYSTEM_SERVER_ROOT.get(this);
    }

    private static URI decodeServerRoot(
        String location)
    {
        return location.indexOf(':') != -1 ? URI.create(location) : new File(location).toURI();
    }
}
