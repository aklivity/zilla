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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class WatchedItem
{
    private final WatchService watchService;
    private final Set<WatchKey> watchKeys;
    private final Path watchedPath;
    private byte[] hash;

    public WatchedItem(
        Path watchedPath,
        WatchService watchService)
    {
        this.watchService = watchService;
        this.watchKeys = new HashSet<>();
        this.watchedPath = watchedPath;
    }

    public Set<WatchKey> keys()
    {
        return watchKeys;
    }

    public void register()
    {
        try
        {
            String scheme = watchedPath.getFileSystem().provider().getScheme();
            if ("file".equals(scheme))
            {
                registerFilePath();
            }
            else if ("http".equals(scheme))
            {
                watchKeys.add(watchedPath.register(watchService));
            }
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
    }

    private void registerFilePath() throws IOException
    {
        Set<Path> watchedPaths = new HashSet<>();
        Deque<Path> observablePaths = new LinkedList<>();
        observablePaths.addLast(watchedPath);

        while (!observablePaths.isEmpty())
        {
            Path observablePath = observablePaths.removeFirst();

            if (watchedPaths.add(observablePath))
            {
                if (Files.isSymbolicLink(observablePath))
                {
                    Path targetPath = Files.readSymbolicLink(observablePath);
                    targetPath = watchedPath.resolveSibling(targetPath).normalize();
                    observablePaths.addLast(targetPath);
                }

                for (Path ancestorPath = observablePath.getParent();
                     ancestorPath != null;
                     ancestorPath = ancestorPath.getParent())
                {
                    if (Files.isSymbolicLink(ancestorPath))
                    {
                        if (watchedPaths.add(ancestorPath))
                        {
                            Path targetPath = Files.readSymbolicLink(ancestorPath);
                            observablePaths.addLast(ancestorPath.resolve(targetPath).normalize());
                        }
                    }
                }
            }
        }
        for (Path watchedPath : watchedPaths)
        {
            if (Files.exists(watchedPath.getParent()))
            {
                WatchKey key = null;
                try
                {
                    key = watchedPath.getParent().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
                }
                catch (IOException ex)
                {
                    rethrowUnchecked(ex);
                }
                watchKeys.add(key);
            }
        }
    }

    public void unregister()
    {
        watchKeys.forEach(WatchKey::cancel);
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
        return !Arrays.equals(hash, newConfigHash);
    }

    public void setHash(
        byte[] newHash)
    {
        hash = newHash;
    }

    public Path getPath()
    {
        return watchedPath;
    }
}
