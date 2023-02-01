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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class WatchedConfig
{
    private final WatchService watchService;
    private final Set<WatchKey> watchKeys;
    private final URL configURL;
    private byte[] configHash;

    public WatchedConfig(
        URL configURL,
        WatchService watchService)
    {
        this.watchService = watchService;
        this.watchKeys = new HashSet<>();
        this.configURL = configURL;
    }

    public Map<WatchKey, WatchedConfig> registerPaths()
    {
        Map<WatchKey, WatchedConfig> watchedConfigsByKey = new IdentityHashMap<>();
        Path configPath = Paths.get(configURL.getPath()).toAbsolutePath();
        try
        {
            Set<Path> watchedPaths = new HashSet<>();

            Deque<Path> observablePaths = new LinkedList<>();
            observablePaths.addLast(configPath);

            if (Files.isSymbolicLink(configPath))
            {
                Path targetPath = Files.readSymbolicLink(configPath);
                if (!targetPath.isAbsolute())
                {
                    targetPath = configPath.getParent().resolve(targetPath);
                }
                // This is needed so if the symlink contains a symlink chain, those are watched as well
                observablePaths.addLast(targetPath);
                // We need to watch for the actual file changes as well
                watchedPaths.add(toRealPath(targetPath));
            }
            while (!observablePaths.isEmpty())
            {
                Path observablePath = observablePaths.removeFirst();

                if (watchedPaths.add(observablePath))
                {
                    for (Path ancestorPath = observablePath.getParent();
                         ancestorPath != null;
                         ancestorPath = ancestorPath.getParent())
                    {
                        if (Files.isSymbolicLink(ancestorPath))
                        {
                            if (watchedPaths.add(ancestorPath))
                            {
                                Path targetPath = Files.readSymbolicLink(ancestorPath);
                                observablePaths.addLast(ancestorPath.resolve(targetPath));
                            }
                        }
                    }
                }
            }
            for (Path watchedPath : watchedPaths)
            {
                if (Files.exists(watchedPath.getParent()))
                {
                    WatchKey key = registerPath(watchedPath.getParent());
                    watchKeys.add(key);
                    watchedConfigsByKey.put(key, this);
                }
            }
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
        return watchedConfigsByKey;
    }

    public void cancelKeys(
        Map<WatchKey, WatchedConfig> watchedConfigsByKey)
    {
        watchKeys.forEach(key ->
            {
                key.cancel();
                watchedConfigsByKey.remove(key);
            }
        );
        watchKeys.clear();
    }

    public boolean isWatchedKey(
        WatchKey key)
    {
        return watchKeys.contains(key);
    }

    public boolean isReconfigureNeeded(
        byte[] newConfigHash)
    {
        return !Arrays.equals(configHash, newConfigHash);
    }

    public void setConfigHash(
        byte[] newConfigHash)
    {
        configHash = newConfigHash;
    }

    public URL getURL()
    {
        return configURL;
    }

    private WatchKey registerPath(
        Path configPath)
    {
        WatchKey key = null;
        try
        {
            key = configPath.register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
        return key;
    }

    private Path toRealPath(
        Path configPath)
    {
        try
        {
            configPath = configPath.toRealPath();
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
        return configPath;
    }

}
