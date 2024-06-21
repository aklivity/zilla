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

import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

public final class HttpFileSystem extends FileSystem
{
    private static final String SEPARATOR = "/";

    private final AbstractHttpFileSystemProvider provider;
    private final URI root;

    HttpFileSystem(
        AbstractHttpFileSystemProvider provider,
        URI root)
    {
        this.provider = provider;
        this.root = root;
    }

    @Override
    public FileSystemProvider provider()
    {
        return provider;
    }

    @Override
    public void close()
    {
        provider.closeFileSystem(root);
    }

    @Override
    public boolean isOpen()
    {
        return true;
    }

    @Override
    public boolean isReadOnly()
    {
        return true;
    }

    @Override
    public String getSeparator()
    {
        return SEPARATOR;
    }

    @Override
    public Iterable<Path> getRootDirectories()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Iterable<FileStore> getFileStores()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Set<String> supportedFileAttributeViews()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path getPath(
        String first,
        String... more)
    {
        requireNonNull(first);
        requireNonNull(more);
        String second = String.join(SEPARATOR, more);
        String path = second.isBlank() ? first : first + SEPARATOR + second;
        Path result = null;
        try
        {
            result = new HttpPath(this, URI.create(path));
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return result;
    }

    @Override
    public PathMatcher getPathMatcher(
        String syntaxAndPattern)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService()
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public HttpWatchService newWatchService()
    {
        return new HttpWatchService();
    }

    HttpPath resolveSibling(
        String other)
    {
        return new HttpPath(this, root.resolve(URI.create(other)));
    }
}
