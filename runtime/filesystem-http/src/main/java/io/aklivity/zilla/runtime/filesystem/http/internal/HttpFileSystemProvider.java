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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class HttpFileSystemProvider extends FileSystemProvider
{
    private final Map<URI, HttpFileSystem> fileSystems = new ConcurrentHashMap<>();

    @Override
    public String getScheme()
    {
        return "http";
    }

    @Override
    @SuppressWarnings("resource")
    public FileSystem newFileSystem(
        URI uri,
        Map<String, ?> env)
    {
        checkURI(uri);

        URI rootURI = uri.resolve("/");
        return fileSystems.compute(rootURI, (u, fs) -> computeHttpFileSystem(u, fs, env));
    }

    @Override
    public FileSystem getFileSystem(
        URI uri)
    {
        checkURI(uri);
        URI rootURI = uri.resolve("/");
        HttpFileSystem hfs = fileSystems.get(rootURI);
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
        checkURI(uri);
        URI rootURI = uri.resolve("/");
        HttpFileSystem hfs = fileSystems.computeIfAbsent(rootURI, this::newHttpFileSystem);
        return hfs.getPath(uri);
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
        HttpPath httpPath = checkPath(path);
        return new ByteArrayInputStream(httpPath.readBody());
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
        HttpPath httpPath = checkPath(path);
        return new ReadOnlyByteArrayChannel(httpPath.readBody());
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
        Objects.requireNonNull(path);
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

    private void checkURI(
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

    private HttpPath checkPath(
        Path path)
    {
        if (!path.getFileSystem().provider().getScheme().equalsIgnoreCase(getScheme()))
        {
            throw new IllegalArgumentException("Scheme does not match this provider");
        }
        return HttpPath.class.cast(path);
    }

    private HttpFileSystem computeHttpFileSystem(
        URI uri,
        HttpFileSystem hfs,
        Map<String, ?> env)
    {
        if (hfs != null)
        {
            throw new FileSystemAlreadyExistsException();
        }

        return new HttpFileSystem(this, uri, env);
    }

    private HttpFileSystem newHttpFileSystem(
        URI uri)
    {
        return new HttpFileSystem(this, uri, Map.of());
    }
}
