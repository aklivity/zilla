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

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HttpWatchService implements WatchService, Callable<Void>
{
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HTTP_2)
        .followRedirects(NORMAL)
        .build();
    private static final Path CLOSE_PATH = Path.of(URI.create("http://localhost:12345"));
    private static final byte[] EMPTY_BODY = new byte[0];

    private final WatchKey closeKey = new HttpWatchKey(CLOSE_PATH);

    private final HttpFileSystem fileSystem;
    private final ScheduledExecutorService executor;
    private final LinkedBlockingQueue<WatchKey> pendingKeys;
    private final BlockingQueue<Path> pathQueue;
    private final Map<Path, WatchKey> watchKeys;
    private final Map<Path, String> etags;
    private final Map<Path, byte[]> hashes;
    private final Map<Path, CompletableFuture<Void>> futures;
    private final MessageDigest md5;

    private int pollSeconds;
    private volatile boolean closed;

    public HttpWatchService(
        HttpFileSystem fileSystem)
    {
        this.fileSystem = fileSystem;
        this.executor = Executors.newScheduledThreadPool(2);
        this.pendingKeys = new LinkedBlockingQueue<>();
        this.watchKeys = new ConcurrentHashMap<>();
        this.pathQueue = new LinkedBlockingQueue<>();
        this.etags = new ConcurrentHashMap<>();
        this.hashes = new ConcurrentHashMap<>();
        this.futures = new ConcurrentHashMap<>();
        this.md5 = initMessageDigest("MD5");
        this.pollSeconds = 30;
        this.closed = false;
        executor.submit(this);
    }

    @Override
    public Void call() throws Exception
    {
        while (true)
        {
            Path path = pathQueue.take();
            if (path == CLOSE_PATH)
            {
                break;
            }
            String etag = etags.getOrDefault(path, "");
            System.out.println("HWS call take path " + path + " etag [" + etag + "]"); // TODO: Ati
            sendAsync(path, etag);
        }
        return null;
    }

    @Override
    public void close()
    {
        closed = true;
        fileSystem.body(null);
        pendingKeys.clear();
        pendingKeys.offer(closeKey);
        futures.values().forEach(future -> future.cancel(true));
        pathQueue.add(CLOSE_PATH);
        watchKeys.clear();
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

    WatchKey register(
        final HttpPath path,
        WatchEvent.Kind<?>[] events,
        WatchEvent.Modifier... modifiers)
    {
        System.out.printf("HWS register path: %s\n", path); // TODO: Ati
        WatchKey watchKey = watchKeys.computeIfAbsent(path, i -> new HttpWatchKey(path));
        pathQueue.offer(path);
        return watchKey;
    }

    private void sendAsync(
        Path path,
        String etag)
    {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .GET()
            .uri(path.toUri())
            .timeout(TIMEOUT);
        if (etag != null && !etag.isEmpty())
        {
            requestBuilder = requestBuilder.headers("If-None-Match", etag, "Prefer", "wait=86400");
        }

        System.out.println("HWS sendAsync path " + path + " etag " + etag); // TODO: Ati
        CompletableFuture<Void> future = HTTP_CLIENT.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
            .thenAccept(this::handleChange)
            .exceptionally(ex -> handleException(ex, path));
        futures.put(path, future);
    }

    private Void handleException(
        Throwable throwable,
        Path path)
    {
        System.out.println("HWS handleException " + throwable.getMessage()); // TODO: Ati
        scheduleRequest(path, pollSeconds);
        return null;
    }

    private void handleChange(
        HttpResponse<byte[]> response)
    {
        System.out.println("HWS handleChange response: " + response); // TODO: Ati
        System.out.println("HWS handleChange response.headers: " + response.headers()); // TODO: Ati
        System.out.println("HWS handleChange response.body: " + new String(response.body())); // TODO: Ati
        Path path = Path.of(response.request().uri());
        int statusCode = response.statusCode();
        int pollSeconds = 0;
        if (statusCode == 404)
        {
            fileSystem.body(EMPTY_BODY);
            addEvent(path);
            pollSeconds = this.pollSeconds;
        }
        else if (statusCode >= 500 && statusCode <= 599)
        {
            fileSystem.body(EMPTY_BODY);
            pollSeconds = this.pollSeconds;
        }
        else
        {
            byte[] body = response.body();
            fileSystem.body(body);
            Optional<String> etagOptional = response.headers().firstValue("Etag");
            if (etagOptional.isPresent())
            {
                String oldEtag = etags.getOrDefault(path, "");
                String newEtag = etagOptional.get();
                if (!oldEtag.equals(newEtag))
                {
                    etags.put(path, newEtag);
                    addEvent(path);

                }
                else if (response.statusCode() != 304)
                {
                    pollSeconds = this.pollSeconds;
                }
            }
            else
            {
                byte[] hash = hashes.get(path);
                byte[] newHash = computeHash(body);
                if (!Arrays.equals(hash, newHash))
                {
                    hashes.put(path, newHash);
                    addEvent(path);
                }
                pollSeconds = this.pollSeconds;
            }
        }
        futures.remove(path);
        scheduleRequest(path, pollSeconds);
    }

    private void addEvent(
        Path path)
    {
        System.out.println("HWS addEvent path " + path); // TODO: Ati
        HttpWatchKey key = (HttpWatchKey) watchKeys.get(path);
        if (key != null)
        {
            key.addEvent(ENTRY_MODIFY, path);
            enqueueKey(key);
        }
    }

    private void scheduleRequest(
        Path path,
        int pollSeconds)
    {
        if (pollSeconds == 0)
        {
            System.out.println("HWS scheduleRequest 0"); // TODO: Ati
            pathQueue.add(path);
        }
        else
        {
            System.out.println("HWS scheduleRequest " + pollSeconds); // TODO: Ati
            executor.schedule(() -> pathQueue.add(path), pollSeconds, TimeUnit.SECONDS);
        }
    }

    private byte[] computeHash(
        byte[] body)
    {
        return md5.digest(body);
    }

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

    private final class HttpWatchKey implements WatchKey
    {
        private final Path path;

        private List<WatchEvent<?>> events = Collections.synchronizedList(new LinkedList<>());

        private volatile boolean valid;

        private HttpWatchKey(
            Path path)
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
            watchKeys.remove(path);
            valid = false;
        }

        @Override
        public Watchable watchable()
        {
            return path;
        }

        void addEvent(
            WatchEvent.Kind<Path> kind,
            Path context)
        {
            Event<Path> ev = new Event<>(kind, context);
            events.add(ev);
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
