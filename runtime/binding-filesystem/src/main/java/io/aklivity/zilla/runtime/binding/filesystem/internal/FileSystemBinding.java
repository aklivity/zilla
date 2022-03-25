/*
 * Copyright 2021-2022 Aklivity Inc
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

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class FileSystemBinding implements Binding
{
    public static final String NAME = "filesystem";

    private final FileSystemConfiguration config;

    FileSystemBinding(
        FileSystemConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return FileSystemBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource(String.format("schema/%s.schema.patch.json", NAME));
    }

    @Override
    public FileSystemBindingContext supply(
        EngineContext context)
    {
        return new FileSystemBindingContext(config, context);
    }
}
