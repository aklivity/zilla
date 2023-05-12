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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.net.URL;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public class FileWatcherTask extends WatcherTask
{
    private final Map<WatchKey, WatchedConfig> watchedConfigs;
    private final WatchService watchService;
    private final Function<String, String> readURL;

    public FileWatcherTask(
        Function<String, String> readURL,
        BiFunction<URL, String, NamespaceConfig> changeListener)
    {
        super(changeListener);
        this.readURL = readURL;
        this.watchedConfigs = new IdentityHashMap<>();
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

                WatchedConfig watchedConfig = watchedConfigs.get(key);

                if (watchedConfig != null && watchedConfig.isWatchedKey(key))
                {
                    // Even if no reconfigure needed, recalculation is necessary, since symlinks might have changed.
                    watchedConfig.keys().forEach(watchedConfigs::remove);
                    watchedConfig.unregister();
                    watchedConfig.register();
                    watchedConfig.keys().forEach(k -> watchedConfigs.put(k, watchedConfig));
                    String newConfigText = readURL.apply(watchedConfig.getURL().toString());
                    byte[] newConfigHash = computeHash(newConfigText);
                    if (watchedConfig.isReconfigureNeeded(newConfigHash))
                    {
                        watchedConfig.setConfigHash(newConfigHash);
                        changeListener.apply(watchedConfig.getURL(), newConfigText);
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
    public CompletableFuture<NamespaceConfig> watch(
        URL configURL)
    {
        WatchedConfig watchedConfig = new WatchedConfig(configURL, watchService);
        watchedConfig.register();
        watchedConfig.keys().forEach(k -> watchedConfigs.put(k, watchedConfig));
        String configText = readURL.apply(configURL.toString());
        watchedConfig.setConfigHash(computeHash(configText));
        NamespaceConfig config = changeListener.apply(configURL, configText);
        if (config == null)
        {
            return CompletableFuture.failedFuture(new Exception("Parsing of the initial configuration failed."));
        }
        return CompletableFuture.completedFuture(config);
    }

    @Override
    public void close() throws IOException
    {
        watchService.close();
    }
}
