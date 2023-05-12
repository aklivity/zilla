/*
 * Copyright 2021-2023 Aklivity Inc
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


import static io.aklivity.zilla.runtime.binding.filesystem.internal.stream.FileSystemServerFactory.FILE_CHANGED_SIGNAL_ID;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.agrona.CloseHelper.quietClose;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public class FileSystemWatcher implements Callable<Void>
{
    private final Map<WatchKey, Set<WatchedFile>> watchedFiles;
    private final WatchService watchService;
    private final Signaler signaler;


    public FileSystemWatcher(
        Signaler signaler)
    {
        this.watchedFiles = new HashMap<>();
        this.signaler = signaler;
        this.watchService = createWatchService();
    }

    @Override
    public Void call()
    {
        while (true)
        {
            try
            {
                final WatchKey watchKey = watchService.take();
                Set<WatchedFile> changedFiles = watchedFiles.get(watchKey);
                if (changedFiles != null)
                {
                    for (WatchedFile changedFile : changedFiles)
                    {
                        String oldTag = changedFile.getOriginalHash();
                        String newTag = changedFile.calculateHash();
                        if (!oldTag.equals(newTag))
                        {
                            changedFile.cancelTimeoutSignal(signaler);
                            changedFile.keys.forEach(watchedFiles::remove);
                            changedFile.unregister();
                            changedFile.signalChange(signaler);
                        }
                        else
                        {
                            if (changedFile.symlinks.length == 0)
                            {
                                changedFile.keys.forEach(watchedFiles::remove);
                                changedFile.unregister();
                                changedFile.registerWithSymlinks(watchService);
                                changedFile.keys.forEach(key ->
                                    watchedFiles.computeIfAbsent(key, k -> new HashSet<>()).add(changedFile)
                                );
                            }
                            else
                            {
                                watchKey.reset();
                            }
                        }
                    }
                }
            }
            catch (InterruptedException | ClosedWatchServiceException ex)
            {
                quietClose(watchService);
                break;
            }
        }
        return null;
    }

    public void watch(
        WatchedFile watchedFile)
    {
        watchedFile.register(watchService);
        watchedFile.keys.forEach(key ->
            watchedFiles.computeIfAbsent(key, k -> new HashSet<>()).add(watchedFile)
        );
    }

    public void unregister(
        WatchedFile watchedFile)
    {
        watchedFile.keys.forEach(watchedFiles::remove);
        watchedFile.unregister();
    }

    public static final class WatchedFile
    {
        private final Set<WatchKey> keys;
        private final Path resolvedPath;
        private final LinkOption[] symlinks;
        private final Supplier<String> hashSupplier;
        private final String originalHash;
        private final long timeoutId;
        private final long originId;
        private final long routedId;
        private final long replyId;

        public WatchedFile(
            Path resolvedPath,
            LinkOption[] symlinks,
            Supplier<String> hashSupplier,
            String hash,
            long timeoutId,
            long originId,
            long routedId,
            long replyId)
        {
            this.keys = new HashSet<>();
            this.resolvedPath = resolvedPath;
            this.symlinks = symlinks;
            this.hashSupplier = hashSupplier;
            this.originalHash = hash;
            this.timeoutId = timeoutId;
            this.originId = originId;
            this.routedId = routedId;
            this.replyId = replyId;
        }
        public String calculateHash()
        {
            return hashSupplier.get();
        }

        public String getOriginalHash()
        {
            return originalHash;
        }

        public void cancelTimeoutSignal(
            Signaler signaler)
        {
            signaler.cancel(timeoutId);
        }

        public void signalChange(
            Signaler signaler)
        {
            signaler.signalNow(originId, routedId, replyId, FILE_CHANGED_SIGNAL_ID, 0);
        }

        private void register(
            WatchService watchService)
        {
            if (symlinks.length == 0)
            {
                registerWithSymlinks(watchService);
            }
            else
            {
                try
                {
                    WatchKey key = resolvedPath.getParent().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
                    keys.add(key);
                }
                catch (IOException ex)
                {
                    rethrowUnchecked(ex);
                }
            }
        }

        private void registerWithSymlinks(
            WatchService watchService)
        {
            try
            {
                Set<Path> watchedPaths = new HashSet<>();

                Deque<Path> observablePaths = new LinkedList<>();
                observablePaths.addLast(resolvedPath);

                while (!observablePaths.isEmpty())
                {
                    Path observablePath = observablePaths.removeFirst();

                    if (watchedPaths.add(observablePath))
                    {
                        if (Files.isSymbolicLink(observablePath))
                        {
                            Path targetPath = Files.readSymbolicLink(observablePath);
                            targetPath = resolvedPath.resolveSibling(targetPath).normalize();
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
                        WatchKey key = registerPath(watchService, watchedPath.getParent());
                        keys.add(key);
                    }
                }
            }
            catch (IOException ex)
            {
                rethrowUnchecked(ex);
            }
        }

        private void unregister()
        {
            keys.forEach(WatchKey::cancel);
            keys.clear();
        }

        private WatchKey registerPath(
            WatchService watchService,
            Path path)
        {
            WatchKey key = null;
            try
            {
                key = path.register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
            }
            catch (IOException ex)
            {
                rethrowUnchecked(ex);
            }
            return key;
        }
    }

    private static WatchService createWatchService()
    {
        WatchService watchService = null;
        try
        {
            watchService = FileSystems.getDefault().newWatchService();
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
        return watchService;
    }
}
