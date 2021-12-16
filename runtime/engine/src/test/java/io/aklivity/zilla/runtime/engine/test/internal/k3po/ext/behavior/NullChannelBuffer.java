/*
 * Copyright 2021-2021 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import org.jboss.netty.buffer.BigEndianHeapChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffer;

public final class NullChannelBuffer extends BigEndianHeapChannelBuffer
{
    private static final byte[] BUFFER = new byte[0];

    public static final NullChannelBuffer NULL_BUFFER = new NullChannelBuffer();

    private NullChannelBuffer()
    {
        super(BUFFER);
    }

    @Override
    public void clear()
    {
    }

    @Override
    public void readerIndex(
        int readerIndex)
    {
        if (readerIndex != 0)
        {
            throw new IndexOutOfBoundsException("Invalid readerIndex: " + readerIndex + " - Maximum is 0");
        }
    }

    @Override
    public void writerIndex(
        int writerIndex)
    {
        if (writerIndex != 0)
        {
            throw new IndexOutOfBoundsException("Invalid writerIndex: " + writerIndex + " - Maximum is 0");
        }
    }

    @Override
    public void setIndex(
        int readerIndex,
        int writerIndex)
    {
        if (writerIndex != 0 || readerIndex != 0)
        {
            throw new IndexOutOfBoundsException(
                    "Invalid writerIndex: " + writerIndex + " - Maximum is " + readerIndex + " or " + capacity());
        }
    }

    @Override
    public void markReaderIndex()
    {
    }

    @Override
    public void resetReaderIndex()
    {
    }

    @Override
    public void markWriterIndex()
    {
    }

    @Override
    public void resetWriterIndex()
    {
    }

    @Override
    public void discardReadBytes()
    {
    }

    @Override
    public ChannelBuffer readBytes(
        int length)
    {
        checkReadableBytes(length);
        return this;
    }

    @Override
    public ChannelBuffer readSlice(
        int length)
    {
        checkReadableBytes(length);
        return this;
    }

    @Override
    public void readBytes(
        byte[] dst,
        int dstIndex,
        int length)
    {
        checkReadableBytes(length);
    }

    @Override
    public void readBytes(
        byte[] dst)
    {
        checkReadableBytes(dst.length);
    }

    @Override
    public void readBytes(
        ChannelBuffer dst)
    {
        checkReadableBytes(dst.writableBytes());
    }

    @Override
    public void readBytes(
        ChannelBuffer dst,
        int length)
    {
        checkReadableBytes(length);
    }

    @Override
    public void readBytes(
        ChannelBuffer dst,
        int dstIndex,
        int length)
    {
        checkReadableBytes(length);
    }

    @Override
    public void readBytes(
        ByteBuffer dst)
    {
        checkReadableBytes(dst.remaining());
    }

    @Override
    public int readBytes(
        GatheringByteChannel out,
        int length) throws IOException
    {
        checkReadableBytes(length);
        return 0;
    }

    @Override
    public void readBytes(
        OutputStream out,
        int length) throws IOException
    {
        checkReadableBytes(length);
    }

    @Override
    public void skipBytes(
        int length)
    {
        checkReadableBytes(length);
    }

    @Override
    public void writeBytes(
        byte[] src,
        int srcIndex,
        int length)
    {
        checkWritableBytes(length);
    }

    @Override
    public void writeBytes(
        ChannelBuffer src,
        int length)
    {
        checkWritableBytes(length);
    }

    @Override
    public void writeBytes(
        ChannelBuffer src,
        int srcIndex,
        int length)
    {
        checkWritableBytes(length);
    }

    @Override
    public void writeBytes(
        ByteBuffer src)
    {
        checkWritableBytes(src.remaining());
    }

    @Override
    public int writeBytes(
        InputStream in,
        int length) throws IOException
    {
        checkWritableBytes(length);
        return 0;
    }

    @Override
    public int writeBytes(
        ScatteringByteChannel in,
        int length) throws IOException
    {
        checkWritableBytes(length);
        return 0;
    }

    @Override
    public void writeZero(
        int length)
    {
        checkWritableBytes(length);
    }

    private void checkWritableBytes(
        int length)
    {
        if (length != 0)
        {
            throw new IndexOutOfBoundsException("Cannot write more than 0 bytes");
        }
    }

    @Override
    protected void checkReadableBytes(
        int length)
    {
        if (length != 0)
        {
            throw new IndexOutOfBoundsException("Only 0 bytes are readable");
        }
    }

}
