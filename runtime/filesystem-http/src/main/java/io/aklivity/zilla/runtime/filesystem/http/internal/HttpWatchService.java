/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.filesystem.http.internal;

import static java.lang.System.currentTimeMillis;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class HttpWatchService implements WatchService
{
    private final WatchKey closeKey = new HttpWatchKey();

    private final Duration pollInterval;
    private final Collection<HttpWatchKey> watchKeys;
    private final BlockingQueue<WatchKey> pendingKeys;
    private final ScheduledExecutorService executor;

    private volatile boolean closed;

    HttpWatchService(
        HttpFileSystemConfiguration config)
    {
        this.pollInterval = config.pollInterval();
        this.watchKeys = new ConcurrentSkipListSet<>();
        this.pendingKeys = new LinkedBlockingQueue<>();
        this.executor = Executors.newScheduledThreadPool(2);
    }

    @Override
    public void close()
    {
        watchKeys.forEach(HttpWatchKey::cancel);
        watchKeys.clear();

        closed = true;
        pendingKeys.clear();
        pendingKeys.offer(closeKey);

        executor.shutdownNow();

        try
        {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException ex)
        {
            rethrowUnchecked(ex);
        }
    }

    @Override
    public WatchKey poll()
    {
        checkOpen();
        WatchKey key = pendingKeys.poll();
        return key != closeKey ? key : null;
    }

    @Override
    public WatchKey poll(
        long timeout,
        TimeUnit unit) throws InterruptedException
    {
        checkOpen();
        WatchKey key = pendingKeys.poll(timeout, unit);
        return key != closeKey ? key : null;
    }

    @Override
    public WatchKey take() throws InterruptedException
    {
        checkOpen();
        WatchKey key = pendingKeys.take();
        return key != closeKey ? key : null;
    }

    HttpWatchKey register(
        HttpPath path,
        WatchEvent.Kind<?>[] events,
        WatchEvent.Modifier... modifiers)
    {
        checkEvents(events);
        checkModifiers(modifiers);

        HttpWatchKey watchKey = new HttpWatchKey(this, path);
        watchKeys.add(watchKey);
        watchKey.watch();

        return watchKey;
    }

    private void checkOpen()
    {
        if (closed)
        {
            throw new ClosedWatchServiceException();
        }
    }

    private void checkEvents(
        WatchEvent.Kind<?>[] events)
    {
        for (WatchEvent.Kind<?> event : events)
        {
            if (!event.equals(ENTRY_CREATE) &&
                !event.equals(ENTRY_MODIFY) &&
                !event.equals(ENTRY_DELETE))
            {
                throw new IllegalArgumentException(String.format("%s event kind not supported", event));
            }
        }
    }

    private void checkModifiers(
        WatchEvent.Modifier[] modifiers)
    {
        if (modifiers.length > 0)
        {
            throw new IllegalArgumentException("Modifiers are not supported");
        }
    }

    private void watchBody(
        HttpWatchKey watchKey)
    {
        long elapsed = currentTimeMillis() - watchKey.lastWatchAt;
        long delay = Math.max(SECONDS.toMillis(pollInterval.getSeconds()) - elapsed, 0L);

        executor.schedule(watchKey::watchBody, delay, MILLISECONDS);
    }

    private void signalKey(
        HttpWatchKey watchKey)
    {
        pendingKeys.offer(watchKey);
    }

    private void cancelKey(
        HttpWatchKey watchKey)
    {
        watchKeys.remove(watchKey);
    }

    private static final class HttpWatchKey implements WatchKey, Comparable<HttpWatchKey>
    {
        private final HttpWatchService watcher;
        private final HttpPath path;

        private List<WatchEvent<?>> watchEvents = Collections.synchronizedList(new LinkedList<>());

        private volatile boolean valid;
        private volatile CompletableFuture<Void> future;
        private long lastWatchAt;

        private HttpWatchKey()
        {
            this.watcher = null;
            this.path = null;
            this.valid = false;
        }

        private HttpWatchKey(
            HttpWatchService watcher,
            HttpPath path)
        {
            this.watcher = watcher;
            this.path = path;
            this.valid = true;
        }

        @Override
        public boolean isValid()
        {
            return valid;
        }

        @Override
        public List<WatchEvent<?>> pollEvents()
        {
            List<WatchEvent<?>> result = watchEvents;
            watchEvents = Collections.synchronizedList(new LinkedList<>());
            return result;
        }

        @Override
        public boolean reset()
        {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void cancel()
        {
            future.cancel(true);
            watcher.cancelKey(this);
            valid = false;
        }

        @Override
        public HttpPath watchable()
        {
            return path;
        }

        @Override
        public boolean equals(
            Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            HttpWatchKey that = (HttpWatchKey) o;
            return Objects.equals(path, that.path);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(path);
        }

        @Override
        public int compareTo(
            HttpWatchKey that)
        {
            return path.compareTo(that.path);
        }

        private void watch()
        {
            if (valid)
            {
                watcher.watchBody(this);
            }
        }

        private void watchBody()
        {
            HttpClient client = path.getFileSystem().client();
            HttpRequest request = path.newWatchRequest();

            this.lastWatchAt = currentTimeMillis();

            this.future = client.sendAsync(request, BodyHandlers.ofByteArray())
                .thenAccept(this::success)
                .exceptionally(this::failure);
        }

        private void success(
            HttpResponse<byte[]> response)
        {
            int changeCount = path.changeCount();
            boolean exists = path.exists();

            path.success(response);

            if (path.changeCount() != changeCount)
            {
                if (exists == path.exists())
                {
                    signalEvent(ENTRY_MODIFY);
                }
                else if (exists)
                {
                    signalEvent(ENTRY_DELETE);
                }
                else
                {
                    signalEvent(ENTRY_CREATE);
                }

                this.lastWatchAt = 0L;
            }

            watcher.watchBody(this);
        }

        private Void failure(
            Throwable ex)
        {
            int changeCount = path.changeCount();
            boolean exists = path.exists();

            path.failure(ex);

            if (path.changeCount() != changeCount)
            {
                if (exists == path.exists())
                {
                    signalEvent(ENTRY_MODIFY);
                }
                else if (exists)
                {
                    signalEvent(ENTRY_DELETE);
                }
                else
                {
                    signalEvent(ENTRY_CREATE);
                }
            }

            // (back off)?
            watcher.watchBody(this);

            return null;
        }

        private void signalEvent(
            WatchEvent.Kind<Path> kind)
        {
            watchEvents.add(new HttpWatchEvent(kind, path));
            watcher.signalKey(this);
        }

        private static class HttpWatchEvent implements WatchEvent<Path>
        {
            private final WatchEvent.Kind<Path> kind;
            private final Path context;
            private final int count;

            HttpWatchEvent(
                WatchEvent.Kind<Path> type,
                Path context)
            {
                this.kind = type;
                this.context = context;
                this.count = 1;
            }

            @Override
            public WatchEvent.Kind<Path> kind()
            {
                return kind;
            }

            @Override
            public Path context()
            {
                return context;
            }

            @Override
            public int count()
            {
                return count;
            }
        }
    }
}
