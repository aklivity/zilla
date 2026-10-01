/*
 * Copyright 2021-2026 Aklivity Inc
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
import java.io.InputStream;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public class FileSystemWatcher implements Callable<Void>
{
    private static final int DIGEST_BUFFER_CAPACITY = 8192;

    private final Map<WatchKey, Set<WatchedFile>> watchedFiles;
    private final List<WatchedFile> signaledFiles;
    private final List<WatchKey> previousKeys;
    private final FileSystemDigest digest;
    private final WatchService watchService;
    private final Signaler signaler;


    public FileSystemWatcher(
        Signaler signaler)
    {
        this.watchedFiles = new HashMap<>();
        this.signaledFiles = new ArrayList<>();
        this.previousKeys = new ArrayList<>();
        this.digest = new FileSystemDigest(new byte[DIGEST_BUFFER_CAPACITY]);
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
                onWatchKeySignaled(watchKey);
            }
            catch (InterruptedException | ClosedWatchServiceException ex)
            {
                quietClose(watchService);
                break;
            }
        }
        return null;
    }

    private synchronized void onWatchKeySignaled(
        WatchKey watchKey)
    {
        watchKey.pollEvents();

        Set<WatchedFile> files = watchedFiles.get(watchKey);
        if (files != null)
        {
            signaledFiles.addAll(files);
            for (WatchedFile signaledFile : signaledFiles)
            {
                String oldTag = signaledFile.getOriginalHash();
                String newTag = calculateHash(signaledFile);
                if (!oldTag.equals(newTag))
                {
                    signaledFile.cancelTimeoutSignal(signaler);
                    release(signaledFile);
                    signaledFile.signalChange(signaler);
                }
                else if (signaledFile.symlinks.length == 0)
                {
                    rewatch(signaledFile);
                }
            }
            signaledFiles.clear();
        }

        if (watchedFiles.containsKey(watchKey) && !watchKey.reset())
        {
            for (WatchedFile watchedFile : watchedFiles.remove(watchKey))
            {
                watchedFile.keys.remove(watchKey);
            }
        }
    }

    String calculateHash(
        WatchedFile watchedFile)
    {
        String hash = null;
        try (InputStream input = watchedFile.input.get())
        {
            if (input != null)
            {
                hash = digest.digest(input);
            }
        }
        catch (IOException ex)
        {
            // reject
        }
        return hash;
    }

    public synchronized void watch(
        WatchedFile watchedFile)
    {
        watchedFile.register(watchService);
        watchedFile.keys.forEach(key -> acquire(key, watchedFile));
    }

    public synchronized void unregister(
        WatchedFile watchedFile)
    {
        release(watchedFile);
    }

    private void rewatch(
        WatchedFile watchedFile)
    {
        previousKeys.addAll(watchedFile.keys);
        watchedFile.keys.clear();
        watchedFile.registerWithSymlinks(watchService);
        watchedFile.keys.forEach(key -> acquire(key, watchedFile));
        for (WatchKey previousKey : previousKeys)
        {
            if (!watchedFile.keys.contains(previousKey))
            {
                release(previousKey, watchedFile);
            }
        }
        previousKeys.clear();
    }

    private void acquire(
        WatchKey key,
        WatchedFile watchedFile)
    {
        watchedFiles.computeIfAbsent(key, k -> new HashSet<>()).add(watchedFile);
    }

    private void release(
        WatchedFile watchedFile)
    {
        watchedFile.keys.forEach(key -> release(key, watchedFile));
        watchedFile.keys.clear();
    }

    private void release(
        WatchKey key,
        WatchedFile watchedFile)
    {
        Set<WatchedFile> files = watchedFiles.get(key);
        if (files != null && files.remove(watchedFile) && files.isEmpty())
        {
            watchedFiles.remove(key);
            key.cancel();
        }
    }

    public static final class WatchedFile
    {
        private final Set<WatchKey> keys;
        private final Path resolvedPath;
        private final LinkOption[] symlinks;
        private final Supplier<InputStream> input;
        private final String originalHash;
        private final long timeoutId;
        private final long originId;
        private final long routedId;
        private final long replyId;

        public WatchedFile(
            Path resolvedPath,
            LinkOption[] symlinks,
            Supplier<InputStream> input,
            String hash,
            long timeoutId,
            long originId,
            long routedId,
            long replyId)
        {
            this.keys = new HashSet<>();
            this.resolvedPath = resolvedPath;
            this.symlinks = symlinks;
            this.input = input;
            this.originalHash = hash;
            this.timeoutId = timeoutId;
            this.originId = originId;
            this.routedId = routedId;
            this.replyId = replyId;
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
            signaler.signalNow(originId, routedId, replyId, 0, FILE_CHANGED_SIGNAL_ID, 0);
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
