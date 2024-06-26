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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

final class ReadOnlyByteArrayChannel implements SeekableByteChannel
{
    private final byte[] data;
    private int position;
    private boolean closed;

    ReadOnlyByteArrayChannel(
        byte[] data)
    {
        this.data = data;
        this.position = 0;
        this.closed = false;
    }

    @Override
    public int read(
        ByteBuffer dst) throws IOException
    {
        ensureOpen();
        int bytesRead;
        if (position >= data.length)
        {
            bytesRead = -1;
        }
        else
        {
            bytesRead = Math.min(dst.remaining(), data.length - position);
            dst.put(data, position, bytesRead);
            position += bytesRead;
        }
        return bytesRead;
    }

    @Override
    public int write(
        ByteBuffer src)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public long position() throws IOException
    {
        ensureOpen();
        return position;
    }

    @Override
    public SeekableByteChannel position(
        long newPosition) throws IOException
    {
        ensureOpen();
        if (newPosition < 0 || newPosition > data.length)
        {
            throw new IllegalArgumentException("Position out of bounds");
        }
        this.position = (int) newPosition;
        return this;
    }

    @Override
    public long size() throws IOException
    {
        ensureOpen();
        return data.length;
    }

    @Override
    public SeekableByteChannel truncate(
        long size)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean isOpen()
    {
        return !closed;
    }

    @Override
    public void close()
    {
        closed = true;
    }

    private void ensureOpen() throws IOException
    {
        if (closed)
        {
            throw new ClosedChannelException();
        }
    }
}
