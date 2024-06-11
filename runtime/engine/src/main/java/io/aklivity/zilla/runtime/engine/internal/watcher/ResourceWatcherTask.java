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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

public class ResourceWatcherTask extends WatcherTask
{
    private final Map<WatchKey, WatchedItem> watchedItems;
    private final WatchService watchService;
    private final Consumer<Set<String>> resourceChangeListener;
    private final Function<Path, String> readPath;
    private final Set<String> namespaces;

    public ResourceWatcherTask(
        FileSystem fileSystem,
        Consumer<Set<String>> resourceChangeListener,
        Function<Path, String> readPath)
    {
        this.resourceChangeListener = resourceChangeListener;
        this.readPath = readPath;
        this.watchedItems = new IdentityHashMap<>();
        this.namespaces = new HashSet<>();
        WatchService watchService = null;
        if (!"jar".equals(fileSystem.provider().getScheme())) // we can't watch in jar fs
        {
            try
            {
                watchService = fileSystem.newWatchService();
            }
            catch (Exception ex)
            {
                rethrowUnchecked(ex);
            }
        }
        this.watchService = watchService;

    }

    @Override
    public Future<Void> submit()
    {
        return executor.submit(this);
    }

    @Override
    public Void call()
    {
        if (watchService != null)
        {
            while (true)
            {
                try
                {
                    final WatchKey key = watchService.take();

                    WatchedItem watchedItem = watchedItems.get(key);

                    if (watchedItem != null && watchedItem.isWatchedKey(key))
                    {
                        // Even if no reconfigure needed, recalculation is necessary, since symlinks might have changed.
                        watchedItem.keys().forEach(watchedItems::remove);
                        watchedItem.unregister();
                        watchedItem.register();
                        watchedItem.keys().forEach(k -> watchedItems.put(k, watchedItem));
                        String newText = readPath.apply(watchedItem.getPath());
                        byte[] newHash = computeHash(newText);
                        if (watchedItem.isReconfigureNeeded(newHash))
                        {
                            watchedItem.setHash(newHash);
                            if (resourceChangeListener != null)
                            {
                                resourceChangeListener.accept(namespaces);
                            }
                        }
                    }
                }
                catch (InterruptedException | ClosedWatchServiceException ex)
                {
                    break;
                }
            }
        }
        return null;
    }

    public void watchResource(
        Path resourcePath)
    {
        WatchedItem watchedItem = new WatchedItem(resourcePath, watchService);
        watchedItem.register();
        watchedItem.keys().forEach(k -> watchedItems.put(k, watchedItem));
        String resource = readPath.apply(resourcePath);
        watchedItem.setHash(computeHash(resource));
    }

    public void addNamespace(
        String namespace)
    {
        namespaces.add(namespace);
    }

    public void removeNamespace(
        String namespace)
    {
        namespaces.remove(namespace);
    }

    @Override
    public void close() throws IOException
    {
        if (watchService != null)
        {
            watchService.close();
        }
    }
}
