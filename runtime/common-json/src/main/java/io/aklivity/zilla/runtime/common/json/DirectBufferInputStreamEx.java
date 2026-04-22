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
package io.aklivity.zilla.runtime.common.json;

import java.io.InputStream;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

public final class DirectBufferInputStreamEx extends InputStream
{
    private DirectBufferEx buffer;
    private int offset;
    private int length;
    private int position;
    private int markPosition;

    public void wrap(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
        this.position = 0;
        this.markPosition = 0;
    }

    public int offset()
    {
        return offset;
    }

    public int length()
    {
        return length;
    }

    public DirectBufferEx buffer()
    {
        return buffer;
    }

    @Override
    public boolean markSupported()
    {
        return true;
    }

    @Override
    public void mark(
        int readlimit)
    {
        markPosition = position;
    }

    @Override
    public void reset()
    {
        position = markPosition;
    }

    @Override
    public int available()
    {
        return length - position;
    }

    @Override
    public long skip(
        long n)
    {
        final int skipped = (int) Math.min(n, length - position);
        position += skipped;
        return skipped;
    }

    @Override
    public int read()
    {
        int value = -1;
        if (position < length)
        {
            value = buffer.getByte(offset + position) & 0xff;
            position++;
        }
        return value;
    }

    @Override
    public int read(
        byte[] dst,
        int dstOffset,
        int dstLength)
    {
        int bytesRead = -1;
        final int available = length - position;
        if (available > 0)
        {
            bytesRead = Math.min(dstLength, available);
            buffer.getBytes(offset + position, dst, dstOffset, bytesRead);
            position += bytesRead;
        }
        return bytesRead;
    }

    @Override
    public void close()
    {
    }
}
