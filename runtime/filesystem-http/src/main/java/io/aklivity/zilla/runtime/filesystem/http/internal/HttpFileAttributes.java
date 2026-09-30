/*
 * Copyright 2021-2026 Aklivity Inc
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

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

final class HttpFileAttributes implements BasicFileAttributes
{
    private static final FileTime EPOCH = FileTime.fromMillis(0L);

    private final long size;
    private final String etag;

    HttpFileAttributes(
        long size,
        String etag)
    {
        this.size = size;
        this.etag = etag;
    }

    @Override
    public FileTime lastModifiedTime()
    {
        return EPOCH;
    }

    @Override
    public FileTime lastAccessTime()
    {
        return EPOCH;
    }

    @Override
    public FileTime creationTime()
    {
        return EPOCH;
    }

    @Override
    public boolean isRegularFile()
    {
        return true;
    }

    @Override
    public boolean isDirectory()
    {
        return false;
    }

    @Override
    public boolean isSymbolicLink()
    {
        return false;
    }

    @Override
    public boolean isOther()
    {
        return false;
    }

    @Override
    public long size()
    {
        return size;
    }

    @Override
    public Object fileKey()
    {
        return etag;
    }
}
