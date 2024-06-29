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
package io.aklivity.zilla.runtime.filesystem.http.internal;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public final class HttpPath implements Path
{
    private static final byte[] EMPTY_BODY = new byte[0];

    private final HttpFileSystem fs;
    private final URI location;

    private volatile byte[] body;
    private volatile String etag;

    private volatile int changeCount;
    private volatile int readCount;

    HttpPath(
        HttpFileSystem fs,
        URI location)
    {
        this.fs = Objects.requireNonNull(fs);
        this.location = Objects.requireNonNull(location);
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
        return new HttpPath(fs, location.resolve(URI.create(other)));
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
        WatchEvent.Kind<?>... events) throws IOException
    {
        return register(watcher, events, new WatchEvent.Modifier[0]);
    }

    @Override
    public WatchKey register(
        WatchService watcher,
        WatchEvent.Kind<?>[] events,
        WatchEvent.Modifier... modifiers)
        throws IOException
    {
        HttpWatchService httpWatcher = checkWatcher(watcher);
        return httpWatcher.register(this, events, modifiers);
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
        HttpPath that = (HttpPath) other;

        return location.compareTo(that.location);
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

    byte[] readBody()
    {
        if (readCount == changeCount)
        {
            try
            {
                HttpClient client = fs.client();
                HttpRequest request = newReadRequest();
                HttpResponse<byte[]> response = client.send(request, BodyHandlers.ofByteArray());
                success(response);
            }
            catch (Exception ex)
            {
                failure(ex);
            }
        }

        readCount = changeCount;

        return body;
    }

    void success(
        HttpResponse<byte[]> response)
    {
        final int status = response.statusCode();

        switch (status)
        {
        case HTTP_OK:
        case HTTP_NO_CONTENT:
            byte[] oldBody = body;
            body = status == HTTP_NO_CONTENT ? EMPTY_BODY : response.body();
            etag = response.headers().firstValue("Etag").orElse(null);
            if (body == null ||
                oldBody == null ||
                !Arrays.equals(body, oldBody))
            {
                changeCount++;
            }
            break;
        case HTTP_NOT_FOUND:
            body = EMPTY_BODY;
            etag = null;
            changeCount++;
            break;
        case HTTP_NOT_MODIFIED:
            break;
        }
    }

    Void failure(
        Throwable ex)
    {
        body = HttpPath.EMPTY_BODY;
        etag = null;
        changeCount++;
        return null;
    }

    int changeCount()
    {
        return changeCount;
    }

    boolean exists()
    {
        return body != null;
    }

    private HttpRequest newReadRequest()
    {
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .GET()
            .uri(location);

        if (etag != null && !etag.isEmpty())
        {
            request = request.headers("If-None-Match", etag);
        }

        return request.build();
    }

    HttpRequest newWatchRequest()
    {
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .GET()
            .uri(location);

        if (etag != null)
        {
            request = request.headers("If-None-Match", etag, "Prefer", "wait=86400");
        }

        return request.build();
    }

    private HttpWatchService checkWatcher(
        WatchService watcher)
    {
        requireNonNull(watcher);

        if (!(watcher instanceof HttpWatchService))
        {
            throw new ProviderMismatchException();
        }

        return (HttpWatchService) watcher;
    }
}
