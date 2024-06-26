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
package io.aklivity.zilla.runtime.engine.internal.watcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class EngineConfigWatchTask implements AutoCloseable, Callable<Void>
{
    private final Path configPath;
    private final EngineConfigWatcher watcher;
    private final ExecutorService executor;
    private Map<String, WatchKey> resourceKeys;

    protected EngineConfigWatchTask(
        Path configPath)
    {
        this.configPath = configPath;
        this.watcher = new EngineConfigWatcher(configPath.getFileSystem());
        this.executor = Executors.newScheduledThreadPool(2);
        this.resourceKeys = new HashMap<>();
    }

    public void submit()
    {
        onPathChanged(configPath);
        executor.submit(this);
    }

    public void watch(
        String resource)
    {
        try
        {
            Path resourcePath = configPath.resolveSibling(resource);
            WatchKey resourceKey = watcher.register(resourcePath, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
            resourceKeys.put(resource, resourceKey);
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
    }

    public void unwatch(
        String resource)
    {
        WatchKey resourceKey = resourceKeys.remove(resource);

        if (resourceKey != null)
        {
            resourceKey.cancel();
        }
    }

    @Override
    public final Void call() throws IOException
    {
        watcher.register(configPath, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);

        while (true)
        {
            try
            {
                final WatchKey key = watcher.take();
                final Path watchable = (Path) key.watchable();
                onPathChanged(watchable);
            }
            catch (InterruptedException ex)
            {
                watcher.close();
                break;
            }
        }

        return null;
    }

    @Override
    public final void close()
    {
        executor.shutdownNow();
    }

    protected abstract void onPathChanged(
        Path watchedPath);
}
