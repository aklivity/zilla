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
package io.aklivity.zilla.runtime.filesystem.http;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HttpWatchService implements WatchService
{
    private static final HttpPath CLOSE_PATH = new HttpPath();

    private final WatchKey closeKey = new HttpWatchKey(CLOSE_PATH);

    private final ScheduledExecutorService executor;
    private final List<HttpWatchKey> watchKeys;
    private final LinkedBlockingQueue<WatchKey> pendingKeys;
    private final MessageDigest md5;

    private int pollSeconds;
    private volatile boolean closed;

    public HttpWatchService()
    {
        this.executor = Executors.newScheduledThreadPool(2);
        this.watchKeys = Collections.synchronizedList(new LinkedList<>());
        this.pendingKeys = new LinkedBlockingQueue<>();
        this.md5 = initMessageDigest("MD5");
        // TODO: Ati - HttpFileSystemConfiguration
        this.pollSeconds = 30;
        this.closed = false;
    }

    @Override
    public void close()
    {
        System.out.println("HWS close"); // TODO: Ati
        watchKeys.forEach(HttpWatchKey::cancel);
        watchKeys.clear();
        pendingKeys.clear();
        pendingKeys.offer(closeKey);
        closed = true;
    }

    @Override
    public WatchKey poll()
    {
        checkOpen();
        WatchKey key = pendingKeys.poll();
        checkKey(key);
        return key;
    }

    @Override
    public WatchKey poll(
        long timeout,
        TimeUnit unit) throws InterruptedException
    {
        checkOpen();
        WatchKey key = pendingKeys.poll(timeout, unit);
        checkKey(key);
        return key;
    }

    @Override
    public WatchKey take() throws InterruptedException
    {
        checkOpen();
        WatchKey key = pendingKeys.take();
        checkKey(key);
        return key;
    }

    // TODO: Ati - HttpFileSystemConfiguration
    public void pollSeconds(
        int pollSeconds)
    {
        this.pollSeconds = pollSeconds;
    }

    private void checkOpen()
    {
        if (closed)
        {
            throw new ClosedWatchServiceException();
        }
    }

    private void checkKey(
        WatchKey key)
    {
        if (key == closeKey)
        {
            enqueueKey(closeKey);
        }
        checkOpen();
    }

    private void enqueueKey(
        WatchKey key)
    {
        pendingKeys.offer(key);
    }

    HttpWatchKey register(
        final HttpPath path,
        WatchEvent.Kind<?>[] events,
        WatchEvent.Modifier... modifiers)
    {
        for (WatchEvent.Kind<?> event : events)
        {
            if (!event.equals(ENTRY_MODIFY))
            {
                throw new IllegalArgumentException("Only ENTRY_MODIFY event kind is supported");
            }
        }
        if (modifiers.length > 0)
        {
            throw new IllegalArgumentException("Modifiers are not supported");
        }
        System.out.printf("HWS register path: %s\n", path); // TODO: Ati
        HttpWatchKey watchKey = new HttpWatchKey(path);
        watchKey.watchBody();
        watchKeys.add(watchKey);
        return watchKey;
    }

    // TODO: Ati
    private byte[] computeHash(
        byte[] body)
    {
        return md5.digest(body);
    }

    // TODO: Ati
    private MessageDigest initMessageDigest(
        String algorithm)
    {
        MessageDigest md5 = null;
        try
        {
            md5 = MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
        return md5;
    }

    public final class HttpWatchKey implements WatchKey
    {
        private final HttpPath path;

        private List<WatchEvent<?>> events = Collections.synchronizedList(new LinkedList<>());

        private volatile boolean valid;

        private HttpWatchKey(
            HttpPath path)
        {
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
            List<WatchEvent<?>> result = events;
            events = Collections.synchronizedList(new LinkedList<>());
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
            watchKeys.remove(this);
            path.cancel();
            valid = false;
        }

        @Override
        public HttpPath watchable()
        {
            return path;
        }

        void addEvent(
            WatchEvent.Kind<Path> kind,
            Path context)
        {
            events.add(new Event<>(kind, context));
            enqueueKey(this);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            HttpWatchKey watchKey = (HttpWatchKey) o;
            return Objects.equals(path, watchKey.path);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(path);
        }

        void watchBody()
        {
            if (valid)
            {
                if (path.longPolling())
                {
                    path.watchBody();
                }
                else
                {
                    executor.schedule(path::watchBody, pollSeconds, TimeUnit.SECONDS);
                }
            }
        }

        private static class Event<T> implements WatchEvent<T>
        {
            private final WatchEvent.Kind<T> kind;
            private final T context;
            private final int count;

            Event(
                WatchEvent.Kind<T> type,
                T context)
            {
                this.kind = type;
                this.context = context;
                this.count = 1;
            }

            @Override
            public WatchEvent.Kind<T> kind()
            {
                return kind;
            }

            @Override
            public T context()
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
