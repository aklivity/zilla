/*
 * Copyright 2021-2024 Aklivity Inc.
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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.agrona.CloseHelper;
import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.internal.event.EngineEventContext;

public final class EngineConfigWatcher implements AutoCloseable
{
    private static final Function<String, Function<Path, Set<Path>>> LOOKUP_RESOLVER;
    static
    {
        Map<String, Function<Path, Set<Path>>> resolvers = new HashMap<>();
        resolvers.put("http", Set::of);
        resolvers.put("https", Set::of);

        Function<Path, Set<Path>> defaultResolver = EngineConfigWatcher::resolveWatchables;
        LOOKUP_RESOLVER = scheme -> resolvers.getOrDefault(scheme, defaultResolver);
    }

    private final Function<Path, Set<Path>> resolver;
    private final WatchService watcher;
    private final Map<WatchKey, CompoundWatchKey> compoundKeys;
    private final ExecutorService executor;

    public EngineConfigWatcher(
        EngineConfiguration config,
        EngineEventContext events,
        FileSystem fileSystem)
    {
        this.resolver = LOOKUP_RESOLVER.apply(fileSystem.provider().getScheme());
        this.watcher = newWatchService(config, events, fileSystem);
        this.executor = watcher != null ? Executors.newScheduledThreadPool(2) : null;
        this.compoundKeys = new IdentityHashMap<>();
    }

    public void submit(
        EngineConfigWatchTask task)
    {
        if (executor != null)
        {
            executor.submit(task);
        }
    }

    public WatchKey register(
        Path watchable,
        WatchEvent.Kind<?>... events) throws IOException
    {
        return register(watchable, events, new WatchEvent.Modifier[0]);
    }

    public WatchKey register(
        Path watchable,
        Kind<?>[] events,
        Modifier... modifiers) throws IOException
    {
        WatchKey watchKey = null;

        if (watcher != null)
        {
            watchKey = registerImpl(watchable, events, modifiers);
        }

        return watchKey;
    }

    public WatchKey take() throws InterruptedException
    {
        return watcher != null ? takeImpl() : null;
    }

    @Override
    public void close()
    {
        if (watcher != null)
        {
            CloseHelper.quietClose(watcher);
        }

        if (executor != null)
        {
            executor.shutdownNow();
        }
    }

    private WatchKey registerImpl(
        Path watchable,
        Kind<?>[] events,
        Modifier... modifiers) throws IOException
    {
        Set<Path> watchPaths = resolver.apply(watchable);
        Set<WatchKey> watchKeys = new HashSet<>();

        for (Path watchPath : watchPaths)
        {
            WatchKey registeredKey = watchPath.register(watcher, events, modifiers);
            watchKeys.add(registeredKey);
        }

        CompoundWatchKey compoundKey = new CompoundWatchKey(watchable, watchKeys);
        watchKeys.forEach(k -> compoundKeys.put(k, compoundKey));

        return compoundKey;
    }

    private WatchKey takeImpl() throws InterruptedException
    {
        WatchKey watchKey = watcher.take();
        return compoundKeys.get(watchKey);
    }

    private final class CompoundWatchKey implements WatchKey
    {
        private final Path watchable;
        private final Set<WatchKey> keys;
        private final List<WatchEvent<?>> events;

        CompoundWatchKey(
            Path watchable,
            Set<WatchKey> keys)
        {
            this.watchable = watchable;
            this.keys = keys;
            this.events = new LinkedList<>();
        }

        @Override
        public boolean isValid()
        {
            return keys.stream().allMatch(WatchKey::isValid);
        }

        @Override
        public List<WatchEvent<?>> pollEvents()
        {
            List<WatchEvent<?>> events = this.events;

            events.clear();
            for (WatchKey key : keys)
            {
                // TODO filter watch events
                events.addAll(key.pollEvents());
            }

            return events;
        }

        @Override
        public boolean reset()
        {
            return keys.stream().allMatch(WatchKey::reset);
        }

        @Override
        public void cancel()
        {
            keys.stream().forEach(WatchKey::cancel);
            keys.forEach(compoundKeys::remove);
        }

        @Override
        public Path watchable()
        {
            return watchable;
        }
    }

    private static Set<Path> resolveWatchables(
        Path watchable)
    {
        Set<Path> watchedPaths = new HashSet<>();

        Deque<Path> observablePaths = new LinkedList<>();
        observablePaths.addLast(watchable);

        while (!observablePaths.isEmpty())
        {
            Path observablePath = observablePaths.removeFirst();

            if (watchedPaths.add(observablePath))
            {
                if (Files.isSymbolicLink(observablePath))
                {
                    Path targetPath = readSymbolicLink(observablePath);
                    targetPath = watchable.resolveSibling(targetPath).normalize();
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
                            Path targetPath = readSymbolicLink(ancestorPath);
                            observablePaths.addLast(ancestorPath.resolve(targetPath).normalize());
                        }
                    }
                }
            }
        }

        Set<Path> watchables = new HashSet<>();
        for (Path watchedPath : watchedPaths)
        {
            Path parentPath = watchedPath.getParent();
            if (parentPath != null && Files.exists(parentPath))
            {
                watchables.add(parentPath);
            }
        }

        return watchables;
    }

    private static Path readSymbolicLink(
        Path link)
    {
        Path target = null;

        try
        {
            target = Files.readSymbolicLink(link);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return target;
    }

    private static WatchService newWatchService(
        EngineConfiguration config,
        EngineEventContext events,
        FileSystem fileSystem)
    {
        WatchService watcher = null;

        if (config.configWatch())
        {
            try
            {
                watcher = newWatchService(fileSystem);
            }
            catch (UnsupportedOperationException ex)
            {
                // not supported (optional)
            }
            catch (IOException ex)
            {
                events.configWatcherFailed(0L, ex.getMessage());
            }
            catch (Throwable ex)
            {
                rethrowUnchecked(ex);
            }
        }

        return watcher;
    }

    private static WatchService newWatchService(
        FileSystem fileSystem) throws IOException
    {
        return fileSystem.newWatchService();
    }
}
