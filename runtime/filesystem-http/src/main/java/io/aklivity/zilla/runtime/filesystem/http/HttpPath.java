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
import java.util.concurrent.CompletableFuture;

public class HttpPath implements Path
{
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HTTP_2)
        .followRedirects(NORMAL)
        .build();
    private static final byte[] EMPTY_BODY = new byte[0];

    private final HttpFileSystem fs;
    private final URI location;

    private byte[] body;
    private String etag;
    private CompletableFuture<Void> future;
    //private HttpResponse<byte[]> response;
    private HttpWatchService.HttpWatchKey watchKey;
    private boolean longPolling;

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
        this.future = null;
        this.longPolling = true;
    }

    HttpPath()
    {
        this.fs = null;
        this.location = null;
        this.body = null;
        this.etag = null;
        this.future = null;
        this.longPolling = true;
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
                System.out.println("HP readBody response 304 body " + new String(body));
                // no op
            }
        }
        catch (Exception ex)
        {
            System.out.println("HP readBody exception " + ex);  // TODO: Ati
            rethrowUnchecked(ex);
        }
        return body;
    }

    void watchBody()
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
        //CompletableFuture<Void> future = HTTP_CLIENT.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        //future = HTTP_CLIENT.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        future = HTTP_CLIENT.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
            .thenAccept(this::acceptResponse)
            .exceptionally(this::handleException);
    }

    /*boolean isDone()
    {
        return future == null ? false : future.isDone();
    }*/

    boolean longPolling()
    {
        return longPolling;
    }

    /*HttpResponse<byte[]> poll() throws Exception
    {
        future.get();
        return response;
    }*/

    private void acceptResponse(
        HttpResponse<byte[]> response)
    {
        //this.response = response;
        System.out.println("HP acceptResponse response: " + response); // TODO: Ati
        System.out.println("HP acceptResponse response.headers: " + response.headers()); // TODO: Ati
        //System.out.println("HWS handleChange response.body: " + new String(response.body())); // TODO: Ati
        //HttpPath path = (HttpPath) Path.of(response.request().uri());
        int statusCode = response.statusCode();
        //int pollSeconds = 0;
        //boolean longPolling = this.longPolling;
        if (statusCode == 404)
        {
            body = EMPTY_BODY;
            watchKey.addEvent(ENTRY_MODIFY, this);
            longPolling = false; // TODO: Ati - ?
            //pollSeconds = this.pollSeconds;
        }
        else if (statusCode >= 500 && statusCode <= 599)
        {
            body = null;
            longPolling = false; // TODO: Ati - ?
            //pollSeconds = this.pollSeconds;
        }
        else
        {
            //byte[] body = response.body();
            System.out.println("HP acceptResponse body " + new String(response.body()));
            Optional<String> etagOptional = response.headers().firstValue("Etag");
            if (etagOptional.isPresent())
            {
                //String oldEtag = etags.getOrDefault(path, ""); // TODO: Ati
                String newEtag = etagOptional.get();
                if (!newEtag.equals(etag))
                {
                    //etags.put(path, newEtag); // TODO: Ati
                    etag = newEtag;
                    this.body = response.body();
                    //longPolling = true; // TODO: Ati
                    watchKey.addEvent(ENTRY_MODIFY, this);
                }
                else if (response.statusCode() != 304)
                {
                    this.body = response.body();
                    longPolling = false; // TODO: Ati
                    //pollSeconds = this.pollSeconds;
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
                //pollSeconds = this.pollSeconds;
            }
        }
        //futures.remove(path);
        //scheduleRequest(path, pollSeconds); // ???
        /*try
        {
            Thread.sleep(pollSeconds * 1000); // TODO: Ati
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        watchBody();*/
        watchKey.watchBody();
    }

    private Void handleException(
        Throwable throwable)
    {
        System.out.println("HP handleException " + throwable.getMessage()); // TODO: Ati
        //scheduleRequest(path, pollSeconds);
        return null;
    }
}
