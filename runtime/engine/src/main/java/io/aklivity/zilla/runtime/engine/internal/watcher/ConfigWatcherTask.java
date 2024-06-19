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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.EngineConfig;

public class ConfigWatcherTask extends WatcherTask
{
    private final Map<WatchKey, WatchedItem> watchedItems;
    private final WatchService watchService;
    private final Function<String, EngineConfig> configChangeListener;
    private final Function<Path, String> readPath;

    public ConfigWatcherTask(
        FileSystem fileSystem,
        Function<String, EngineConfig> configChangeListener,
        Function<Path, String> readPath)
    {
        this.configChangeListener = configChangeListener;
        this.readPath = readPath;
        this.watchedItems = new IdentityHashMap<>();
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
                    System.out.println("CWT call key " + key); // TODO: Ati

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
                            if (configChangeListener != null)
                            {
                                configChangeListener.apply(newText);
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

    public void watchConfig(
        Path configPath)
    {
        String configText;
        if ("jar".equals(configPath.getFileSystem().provider().getScheme()))
        {
            configText = readPath.apply(configPath);
        }
        else
        {
            WatchedItem watchedItem = new WatchedItem(configPath, watchService);
            watchedItem.register();
            watchedItem.keys().forEach(k -> watchedItems.put(k, watchedItem));
            System.out.println("CWT watchConfig readPath " + configPath); // TODO: Ati
            configText = readPath.apply(configPath);
            watchedItem.setHash(computeHash(configText));
        }
        configChangeListener.apply(configText);
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
