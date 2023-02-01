/*
 * Copyright 2021-2022 Aklivity Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;


public class FileWatcherTask extends WatcherTask
{
    private final Map<WatchKey, WatchedConfig> watchedConfigsByKey;
    private MessageDigest md5;
    private WatchService watchService;

    public FileWatcherTask(
        BiConsumer<URL, String> changeListener)
    {
        super(changeListener);
        this.watchedConfigsByKey = new IdentityHashMap<>();
        try
        {
            this.md5 = MessageDigest.getInstance("MD5");
            this.watchService = FileSystems.getDefault().newWatchService();
        }
        catch (NoSuchAlgorithmException | IOException ex)
        {
            rethrowUnchecked(ex);
        }

    }

    @Override
    public Void call()
    {
        while (true)
        {
            try
            {
                final WatchKey key = watchService.take();

                WatchedConfig watchedConfig = watchedConfigsByKey.get(key);

                if (watchedConfig != null && watchedConfig.isWatchedKey(key))
                {
                    // Even if no reconfigure needed, recalculation is needed, since symlinks might have changed.
                    watchedConfig.cancelKeys(watchedConfigsByKey);
                    watchedConfigsByKey.putAll(watchedConfig.registerPaths());
                    String newConfigText = readConfigText(watchedConfig.getURL());
                    byte[] newConfigHash = computeHash(newConfigText);
                    if (watchedConfig.isReconfigureNeeded(newConfigHash))
                    {
                        watchedConfig.setConfigHash(newConfigHash);
                        changeListener.accept(watchedConfig.getURL(), newConfigText);
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
    public void onURLDiscovered(
        URL configURL)
    {
        WatchedConfig watchedConfig = new WatchedConfig(configURL, watchService);
        watchedConfigsByKey.putAll(watchedConfig.registerPaths());
        String configText = readConfigText(configURL);
        watchedConfig.setConfigHash(computeHash(configText));
        changeListener.accept(configURL, configText);
    }

    @Override
    public void close() throws IOException
    {
        watchService.close();
    }

    private String readConfigText(URL configURL)
    {
        String configText;
        try
        {
            URLConnection connection = configURL.openConnection();
            try (InputStream input = connection.getInputStream())
            {
                configText = new String(input.readAllBytes(), UTF_8);
            }
        }
        catch (IOException ex)
        {
            return "";
        }
        return configText;
    }

    private byte[] computeHash(
        String configText)
    {
        return md5.digest(configText.getBytes(UTF_8));
    }
}
