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

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HttpPath implements Path
{
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HTTP_2)
        .followRedirects(NORMAL)
        .build();
    private static final byte[] EMPTY_BODY = new byte[0];

    private final HttpFileSystem fs;
    private final URI location;
    private final ScheduledExecutorService executor;
    private int pollSeconds; // TODO: Ati - HttpFileSystemConfiguration -> final

    private byte[] body;
    private String etag;
    private HttpWatchService.HttpWatchKey watchKey;
    private CompletableFuture<Void> future;

    HttpPath(
        HttpFileSystem fs,
        URI location)
    {
        if (!fs.provider().getScheme().equals(location.getScheme()))
        {
            throw new IllegalArgumentException(String.format("invalid protocol: %s", location.getScheme()));
        }
        System.out.println("HP constructor location " + location); // TODO: Ati
        this.fs = fs;
        this.location = location;
        this.executor = Executors.newScheduledThreadPool(2);
        // TODO: Ati - HttpFileSystemConfiguration
        //this.pollSeconds = 30;
        this.pollSeconds = 3;
    }

    HttpPath()
    {
        this.fs = null;
        this.location = null;
        this.executor = null;
        this.pollSeconds = 0;
    }

    @Override
    public HttpFileSystem getFileSystem()
    {
        return fs;
    }

    @Override
    public boolean isAbsolute()
    {
        return true;
    }

    @Override
    public Path getRoot()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path getFileName()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path getParent()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int getNameCount()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path getName(
        int index)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path subpath(
        int beginIndex,
        int endIndex)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean startsWith(
        Path other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean startsWith(
        String other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean endsWith(
        Path other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean endsWith(
        String other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path normalize()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path resolve(
        Path other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path resolve(
        String other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path resolveSibling(
        Path other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path resolveSibling(
        String other)
    {
        return fs.resolveSibling(other);
    }

    @Override
    public Path relativize(
        Path other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public URI toUri()
    {
        return location;
    }

    @Override
    public Path toAbsolutePath()
    {
        return this;
    }

    @Override
    public Path toRealPath(
        LinkOption... options)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public File toFile()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public WatchKey register(
        WatchService watcher,
        WatchEvent.Kind<?>[] events,
        WatchEvent.Modifier... modifiers)
        throws IOException
    {
        requireNonNull(watcher);
        if (!(watcher instanceof HttpWatchService))
        {
            throw new ProviderMismatchException();
        }
        watchKey = ((HttpWatchService) watcher).register(this, events, modifiers);
        return watchKey;
    }

    @Override
    public WatchKey register(
        WatchService watcher,
        WatchEvent.Kind<?>... events) throws IOException
    {
        System.out.println("HP register"); // TODO: Ati
        return register(watcher, events, new WatchEvent.Modifier[0]);
    }

    @Override
    public Iterator<Path> iterator()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int compareTo(
        Path other)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String toString()
    {
        return location.toString();
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

        HttpPath path = (HttpPath) o;
        return Objects.equals(location, path.location);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(location);
    }

    // TODO: Ati - HttpFileSystemConfiguration
    public void pollSeconds(
        int pollSeconds)
    {
        this.pollSeconds = pollSeconds;
    }

    byte[] readBody()
    {
        System.out.println("HP readBody"); // TODO: Ati
        try
        {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .GET()
                .uri(location);
            if (etag != null && !etag.isEmpty())
            {
                requestBuilder = requestBuilder.headers("If-None-Match", etag);
            }
            HttpRequest request = requestBuilder.build();
            System.out.println("HP readBody path " + location + " request " + request + " etag " + etag); // TODO: Ati
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            System.out.println("HP readBody response " + response); // TODO: Ati
            //System.out.println("HP readBody response.body " + new String(response.body())); // TODO: Ati
            if (response.statusCode() == HTTP_OK)
            {
                body = response.body();
                Optional<String> etagOptional = response.headers().firstValue("Etag");
                // TODO: Ati - calculate and store hash if there is no etag
                if (etagOptional.isPresent())
                {
                    etag = etagOptional.get();
                }
            }
            else if (response.statusCode() == HTTP_NOT_FOUND)
            {
                body = new byte[0];
                etag = null;
            }
            else if (response.statusCode() == HTTP_NOT_MODIFIED)
            {
                // no op
            }
        }
        catch (Exception ex)
        {
            System.out.println("HP readBody exception " + ex);  // TODO: Ati
            body = new byte[0];
        }
        return body;
    }

    void watch()
    {
        scheduleWatchBody(this.pollSeconds);
    }

    private void scheduleWatchBody(
        int pollSeconds)
    {
        if (pollSeconds == 0)
        {
            watchBody();
        }
        else
        {
            executor.schedule(this::watchBody, pollSeconds, TimeUnit.SECONDS);
        }
    }

    private void watchBody()
    {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .GET()
            .uri(location);
        if (etag != null && !etag.isEmpty())
        {
            requestBuilder = requestBuilder.headers("If-None-Match", etag, "Prefer", "wait=86400");
        }
        HttpRequest request = requestBuilder.build();
        System.out.println("HP watchBody path " + location + " request " + request + " etag " + etag); // TODO: Ati
        future = HTTP_CLIENT.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
            .thenAccept(this::handleResponse)
            .exceptionally(this::handleException);
    }

    private void handleResponse(
        HttpResponse<byte[]> response)
    {
        System.out.println("HP handleResponse response: " + response); // TODO: Ati
        System.out.println("HP handleResponse response.headers: " + response.headers()); // TODO: Ati
        int statusCode = response.statusCode();
        int pollSeconds = 1;
        if (statusCode == 404)
        {
            body = EMPTY_BODY;
            watchKey.addEvent(ENTRY_MODIFY, this);
            pollSeconds = this.pollSeconds;
        }
        else if (statusCode >= 500 && statusCode <= 599)
        {
            body = null;
            pollSeconds = this.pollSeconds;
        }
        else
        {
            System.out.println("HP handleResponse body " + new String(response.body()));
            Optional<String> etagOptional = response.headers().firstValue("Etag");
            if (etagOptional.isPresent())
            {
                String newEtag = etagOptional.get();
                if (!newEtag.equals(etag))
                {
                    etag = newEtag;
                    body = response.body();
                    watchKey.addEvent(ENTRY_MODIFY, this);
                }
                else if (response.statusCode() != 304)
                {
                    body = response.body();
                    pollSeconds = this.pollSeconds;
                }
            }
            else
            {
                // TODO: Ati - hash
                //byte[] hash = hashes.get(path);
                //byte[] newHash = computeHash(body);
                //if (!Arrays.equals(hash, newHash))
                {
                    //hashes.put(path, newHash); // TODO: Ati
                    //addEvent(ENTRY_MODIFY, path);
                    watchKey.addEvent(ENTRY_MODIFY, this);
                    //addEvent(path);
                }
                pollSeconds = this.pollSeconds;
            }
        }
        scheduleWatchBody(pollSeconds);
    }

    void cancel()
    {
        System.out.println("HP cancel"); // TODO: Ati
        body = null;
        etag = null;
        future.cancel(true);
    }

    // required for testing
    public void shutdown()
    {
        System.out.println("HP shutdown"); // TODO: Ati
        executor.shutdownNow();
    }

    private Void handleException(
        Throwable throwable)
    {
        System.out.println("HP handleException " + throwable.getMessage()); // TODO: Ati
        return null;
    }
}
