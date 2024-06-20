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

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public abstract class AbstractHttpFileSystemProvider extends FileSystemProvider
{
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HTTP_2)
        .followRedirects(NORMAL)
        .build();

    private final Map<URI, HttpFileSystem> fileSystems = new ConcurrentHashMap<>();

    @Override
    public abstract String getScheme();

    @Override
    public FileSystem newFileSystem(
        URI uri,
        Map<String, ?> env)
    {
        checkUri(uri);
        HttpFileSystem hfs = fileSystems.get(uri);
        if (hfs == null)
        {
            hfs = new HttpFileSystem(this, uri);
            fileSystems.put(uri, hfs);
        }
        else
        {
            throw new FileSystemAlreadyExistsException();
        }
        return hfs;
    }

    @Override
    public FileSystem getFileSystem(
        URI uri)
    {
        checkUri(uri);
        HttpFileSystem hfs = fileSystems.get(uri);
        if (hfs == null)
        {
            throw new FileSystemNotFoundException();
        }
        return hfs;
    }

    @Override
    public Path getPath(
        URI uri)
    {
        FileSystem hfs = fileSystems.get(uri);
        if (hfs == null)
        {
            hfs = newFileSystem(uri, Map.of());
        }
        return hfs.getPath(uri.toString());
    }

    @Override
    public FileSystem newFileSystem(
        Path path,
        Map<String, ?> env)
    {
        return newFileSystem(path.toUri(), env);
    }

    @Override
    public InputStream newInputStream(
        Path path,
        OpenOption... options)
    {
        // TODO: Ati - chk if path is http/s -> cast it to HttpPath
        return new ByteArrayInputStream(resolveBody(path));
    }

    @Override
    public OutputStream newOutputStream(
        Path path,
        OpenOption... options)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public FileChannel newFileChannel(
        Path path,
        Set<? extends OpenOption> options,
        FileAttribute<?>... attrs)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(
        Path path,
        Set<? extends OpenOption> options,
        ExecutorService executor,
        FileAttribute<?>... attrs)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public SeekableByteChannel newByteChannel(
        Path path,
        Set<? extends OpenOption> options,
        FileAttribute<?>... attrs)
    {
        // TODO: Ati - chk if path is http/s -> cast it to HttpPath
        return new ReadOnlyByteArrayChannel(resolveBody(path));
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(
        Path dir,
        DirectoryStream.Filter<? super Path> filter)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void createDirectory(
        Path dir,
        FileAttribute<?>... attrs)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void createSymbolicLink(
        Path link,
        Path target, FileAttribute<?>... attrs)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void createLink(
        Path link,
        Path existing)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void delete(
        Path path)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean deleteIfExists(
        Path path)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path readSymbolicLink(
        Path link)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void copy(
        Path source,
        Path target,
        CopyOption... options)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void move(
        Path source,
        Path target,
        CopyOption... options)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean isSameFile(
        Path path,
        Path path2)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean isHidden(
        Path path)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public FileStore getFileStore(
        Path path)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void checkAccess(
        Path path,
        AccessMode... modes)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(
        Path path,
        Class<V> type,
        LinkOption... options)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(
        Path path,
        Class<A> type,
        LinkOption... options)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Map<String, Object> readAttributes(
        Path path,
        String attributes,
        LinkOption... options)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void setAttribute(
        Path path,
        String attribute,
        Object value, LinkOption... options)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    void closeFileSystem(
        URI uri)
    {
        fileSystems.remove(uri);
    }

    private void checkUri(
        URI uri)
    {
        if (!uri.getScheme().equalsIgnoreCase(getScheme()))
        {
            throw new IllegalArgumentException("URI does not match this provider");
        }
        if (uri.getPath() == null)
        {
            throw new IllegalArgumentException("Path component is undefined");
        }
    }

    // TODO: Ati - this should be the new resolveBody
    /*private byte[] resolveBody(
        Path path)
    //HttpPath path)
    {
        // TODO: Ati - this should delegate the call to HttpPath path.resolveBody()
        return readBody(path);
    }*/

    // TODO: Ati - remove this
    private byte[] resolveBody(
        Path path)
    {
        byte[] body;
        //HttpFileSystem fileSystem = fileSystems.get(path.toUri());
        HttpFileSystem fileSystem = (HttpFileSystem) path.getFileSystem();
        if (fileSystem != null && fileSystem.body() != null)
        {
            System.out.println("AHFSP resolveBody fs.body"); // TODO: Ati
            body = fileSystem.body();
        }
        else
        {
            System.out.println("AHFSP resolveBody readBody"); // TODO: Ati
            body = readBody(path);
        }
        return body;
    }

    private static byte[] readBody(
        Path path)
    {
        byte[] body = new byte[0];
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(path.toUri())
                .timeout(TIMEOUT)
                .build();
            System.out.println("AHFSP readBody path " + path + " request " + request); // TODO: Ati
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            //System.out.println("AHFSP readBody response.body " + new String(response.body())); // TODO: Ati
            if (response.statusCode() == HTTP_OK)
            {
                body = response.body();
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
