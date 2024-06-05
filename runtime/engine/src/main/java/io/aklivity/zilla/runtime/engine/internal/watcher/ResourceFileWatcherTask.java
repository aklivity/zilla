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
import java.net.URL;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

public class ResourceFileWatcherTask extends WatcherTask implements ResourceWatcher
{
    private final Map<WatchKey, WatchedItem> watchedItems;
    private final WatchService watchService;
    private final Consumer<Set<String>> resourceChangeListener;
    private final Function<String, String> readURL;
    private final Set<String> namespaces;

    public ResourceFileWatcherTask(
        Consumer<Set<String>> resourceChangeListener,
        Function<String, String> readURL)
    {
        this.resourceChangeListener = resourceChangeListener;
        this.readURL = readURL;
        this.watchedItems = new IdentityHashMap<>();
        this.namespaces = new HashSet<>();
        WatchService watchService = null;

        try
        {
            watchService = FileSystems.getDefault().newWatchService();
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
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
                    String newText = readURL.apply(watchedItem.getURL().toString());
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

        return null;
    }

    @Override
    public void watchResource(
        URL resourceURL)
    {
        WatchedItem watchedItem = new WatchedItem(resourceURL, watchService);
        watchedItem.register();
        watchedItem.keys().forEach(k -> watchedItems.put(k, watchedItem));
        String resource = readURL.apply(resourceURL.toString());
        watchedItem.setHash(computeHash(resource));
    }

    @Override
    public void addNamespace(
        String namespace)
    {
        namespaces.add(namespace);
    }

    @Override
    public void removeNamespace(
        String namespace)
    {
        namespaces.remove(namespace);
    }

    @Override
    public void close() throws IOException
    {
        watchService.close();
    }
}
