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

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Map;
import java.util.Set;

public final class HttpFileSystem extends FileSystem
{
    private static final String HTTP_PATH_SEPARATOR = "/";

    private final HttpFileSystemProvider provider;
    private final URI root;
    private final HttpFileSystemConfiguration config;
    private final HttpClient client;
    private final String authorization;

    HttpFileSystem(
        HttpFileSystemProvider provider,
        URI root,
        Map<String, ?> env)
    {
        this.provider = provider;
        this.root = root;
        this.config = new HttpFileSystemConfiguration(env);
        this.client = HttpClient.newBuilder()
            .version(HTTP_2)
            .followRedirects(NORMAL)
            .build();
        this.authorization = (String) env.get("zilla.engine.config.http.authorization");
    }

    @Override
    public HttpFileSystemProvider provider()
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
        return HTTP_PATH_SEPARATOR;
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

        String path = more.length > 0
            ? first + HTTP_PATH_SEPARATOR + String.join(HTTP_PATH_SEPARATOR, more)
            : first;

        return getPath(URI.create(path));
    }

    public HttpPath getPath(
        URI uri)
    {
        return new HttpPath(this, uri, authorization);
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
        return new HttpWatchService(config, authorization);
    }

    HttpClient client()
    {
        return client;
    }
}
