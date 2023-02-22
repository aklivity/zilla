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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static org.agrona.CloseHelper.quietClose;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ForkJoinTask;

import io.aklivity.zilla.runtime.binding.filesystem.internal.stream.FileSystemServerFactory;
import io.aklivity.zilla.runtime.binding.filesystem.internal.stream.FileSystemStreamFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

final class FileSystemBindingContext implements BindingContext
{
    private final Map<KindConfig, FileSystemStreamFactory> factories;
    private final FileSystemWatcher fileSystemWatcher;
    private final ForkJoinTask<Void> fileSystemWatcherRef;

    FileSystemBindingContext(
        FileSystemConfiguration config,
        EngineContext context)
    {
        Map<KindConfig, FileSystemStreamFactory> factories = new EnumMap<>(KindConfig.class);
        this.factories = factories;
        this.fileSystemWatcher = new FileSystemWatcher(context.signaler());
        this.fileSystemWatcherRef = commonPool().submit(fileSystemWatcher);
        factories.put(SERVER, new FileSystemServerFactory(config, context, fileSystemWatcher));
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        FileSystemStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.attach(binding);
        }

        return factory;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        FileSystemStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.detach(binding.id);
        }
    }

    @Override
    public void close()
    {
        quietClose(fileSystemWatcher);
        try
        {
            fileSystemWatcherRef.get();
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), factories);
    }
}
