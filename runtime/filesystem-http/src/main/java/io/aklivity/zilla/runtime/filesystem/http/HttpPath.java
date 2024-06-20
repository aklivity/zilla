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
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

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

public class HttpPath implements Path
{
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HTTP_2)
        .followRedirects(NORMAL)
        .build();

    private final HttpFileSystem fs;
    private final URI location;

    private byte[] body;
    private String etag;

    HttpPath(
        HttpFileSystem fs,
        URI location)
    {
        if (!fs.provider().getScheme().equals(location.getScheme()))
        {
            throw new IllegalArgumentException(String.format("invalid protocol: %s", location.getScheme()));
        }
        this.fs = fs;
        this.location = location;
        this.body = null;
        this.etag = null;
    }

    HttpPath()
    {
        this.fs = null;
        this.location = null;
        this.body = null;
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
        return ((HttpWatchService) watcher).register(this, events, modifiers);
    }

    @Override
    public WatchKey register(
        WatchService watcher,
        WatchEvent.Kind<?>... events) throws IOException
    {
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

    byte[] resolveBody()
    {
        // TODO: Ati - if we always call readBody, can this be removed?
        System.out.println("HP resolveBody");
        body = readBody();
        /*if (body == null)
        {
            body = readBody();
        }*/
        return body;
    }

    private byte[] readBody()
    {
        //byte[] body = new byte[0];
        try
        {
            // TODO: Ati - add etag to the header
            // TODO: Ati - check+store etag/hash
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .GET()
                .uri(location);
            if (etag != null && !etag.isEmpty())
            {
                //requestBuilder = requestBuilder.headers("If-None-Match", etag, "Prefer", "wait=86400");
                // TODO: Ati - this is a sync call, I guess we don't need Prefer wait
                requestBuilder = requestBuilder.headers("If-None-Match", etag);
            }
            HttpRequest request = requestBuilder.build();
            System.out.println("HP readBody path " + location + " request " + request + " etag " + etag); // TODO: Ati
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            //System.out.println("HP readBody response.body " + new String(response.body())); // TODO: Ati
            if (response.statusCode() == HTTP_OK)
            {
                body = response.body();
                Optional<String> etagOptional = response.headers().firstValue("Etag");
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
            System.out.println("AHFSP readBody exception " + ex);  // TODO: Ati
            rethrowUnchecked(ex);
        }
        return body;
    }
}
