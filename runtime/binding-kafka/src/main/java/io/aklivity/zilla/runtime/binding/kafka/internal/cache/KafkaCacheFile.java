/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntFunction;

import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;

public class KafkaCacheFile implements AutoCloseable
{
    private static final String EXT_LOG = ".log";
    private static final String EXT_DELTA = ".delta";
    private static final String EXT_INDEX = ".index";
    private static final String EXT_HSCAN = ".hscan";
    private static final String EXT_HSCAN_WORK = ".hscan.work";
    private static final String EXT_HINDEX = ".hindex";
    private static final String EXT_NSCAN = ".nscan";
    private static final String EXT_NSCAN_WORK = ".nscan.work";
    private static final String EXT_NINDEX = ".nindex";
    private static final String EXT_KSCAN = ".kscan";
    private static final String EXT_KSCAN_WORK = ".kscan.work";
    private static final String EXT_KINDEX = ".kindex";

    private static final String FORMAT_FILE = "%%019d%s";
    private static final String FORMAT_LOG_FILE = String.format(FORMAT_FILE, EXT_LOG);
    private static final String FORMAT_DELTA_FILE = String.format(FORMAT_FILE, EXT_DELTA);
    private static final String FORMAT_INDEX_FILE = String.format(FORMAT_FILE, EXT_INDEX);
    private static final String FORMAT_HSCAN_FILE = String.format(FORMAT_FILE, EXT_HSCAN);
    private static final String FORMAT_HINDEX_FILE = String.format(FORMAT_FILE, EXT_HINDEX);
    private static final String FORMAT_NSCAN_FILE = String.format(FORMAT_FILE, EXT_NSCAN);
    private static final String FORMAT_NINDEX_FILE = String.format(FORMAT_FILE, EXT_NINDEX);
    private static final String FORMAT_KSCAN_FILE = String.format(FORMAT_FILE, EXT_KSCAN);
    private static final String FORMAT_KINDEX_FILE = String.format(FORMAT_FILE, EXT_KINDEX);

    private final Path location;
    private final MappedByteBuffer mappedByteBuf;
    private final MutableDirectBuffer mappedBuf;
    private final FileChannel appender;
    private final MutableDirectBuffer appendBuf;
    private final ByteBuffer appendByteBuf;

    private volatile int maxCapacity;
    private volatile int capacity;   // only ever increases
    private int markValue;

    public KafkaCacheFile(
        Path location,
        int capacity,
        MutableDirectBuffer appendBuf)
    {
        this.location = location;
        this.mappedByteBuf = mapCreateAppend(location, capacity);
        this.mappedBuf = new UnsafeBuffer(mappedByteBuf);
        this.appender = openAppender(location);
        this.appendBuf = requireNonNull(appendBuf);
        this.appendByteBuf = requireNonNull(appendBuf.byteBuffer());
        this.capacity = 0;
        this.maxCapacity = capacity;
    }

    public KafkaCacheFile(
        Path location)
    {
        this.location = location;
        this.mappedByteBuf = mapReadWrite(location);
        this.mappedBuf = new UnsafeBuffer(mappedByteBuf);
        this.appender = null;
        this.appendBuf = null;
        this.appendByteBuf = null;
        this.capacity = mappedBuf.capacity();
        this.maxCapacity = mappedBuf.capacity();
    }

    public Path location()
    {
        return location;
    }

    public int capacity()
    {
        return capacity;
    }

    public int available()
    {
        return maxCapacity - capacity;
    }

    public void mark()
    {
        this.markValue = capacity;
    }

    public int markValue()
    {
        return markValue;
    }

    public <T> T readBytes(
        int position,
        Flyweight.Visitor<T> visitor)
    {
        return visitor.visit(mappedBuf, position, capacity);
    }

    public long readLong(
        int position)
    {
        return mappedBuf.getLong(position);
    }

    public void writeBytes(
        int position,
        Flyweight flyweight)
    {
        writeBytes(position, flyweight.buffer(), flyweight.offset(), flyweight.sizeof());
    }

    public void writeBytes(
        int position,
        DirectBuffer srcBuffer,
        int srcIndex,
        int length)
    {
        mappedBuf.putBytes(position, srcBuffer, srcIndex, length);
    }

    public void writeLong(
        int position,
        long value)
    {
        mappedBuf.putLong(position, value);
    }

    public void writeInt(
        int position,
        int value)
    {
        mappedBuf.putInt(position, value);
    }

    public void advance(
        int position)
    {
        assert position >= capacity;
        int remaining = position - capacity;

        assert remaining <= maxCapacity;

        while (remaining > 0)
        {
            final int length = Math.min(remaining, appendBuf.capacity());
            appendBytes(appendBuf, 0, length);
            remaining -= length;
        }

    }

    public boolean appendBytes(
        Flyweight flyweight)
    {
        return appendBytes(flyweight.buffer(), flyweight.offset(), flyweight.sizeof());
    }

    public boolean appendBytes(
        DirectBuffer srcBuffer)
    {
        return appendBytes(srcBuffer, 0, srcBuffer.capacity());
    }

    public boolean appendBytes(
        DirectBuffer srcBuffer,
        int srcIndex,
        int length)
    {
        final int available = available();
        final boolean writable = available >= length;

        if (writable)
        {
            try
            {
                final int appendableBytes = appendBuf.capacity();

                int remainingBytes = length;
                while (remainingBytes > 0)
                {
                    final int fragmentBytes = Math.min(remainingBytes, appendableBytes);
                    appendByteBuf.clear();
                    appendBuf.putBytes(0, srcBuffer, srcIndex, fragmentBytes);
                    appendByteBuf.limit(fragmentBytes);

                    int writtenBytes = 0;
                    while (writtenBytes < fragmentBytes)
                    {
                        writtenBytes += appender.write(appendByteBuf);
                    }
                    assert writtenBytes == fragmentBytes : String.format("%d == %d", writtenBytes, fragmentBytes);

                    capacity += writtenBytes;
                    remainingBytes -= fragmentBytes;
                    srcIndex += fragmentBytes;

                    assert remainingBytes >= 0;
                }
                assert capacity <= maxCapacity;
            }
            catch (IOException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return writable;
    }

    public boolean appendLong(
        long value)
    {
        final int available = available();
        final boolean writable = available >= Long.BYTES;

        if (writable)
        {
            try
            {
                appendByteBuf.clear();
                appendBuf.putLong(0, value);
                appendByteBuf.limit(Long.BYTES);

                final int written = appender.write(appendByteBuf);
                assert written == Long.BYTES;

                capacity += written;
                assert capacity <= maxCapacity;
            }
            catch (IOException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return writable;
    }


    public boolean appendInt(
        int value)
    {
        final int available = available();
        final boolean writable = available >= Integer.BYTES;

        if (writable)
        {
            try
            {
                appendByteBuf.clear();
                appendBuf.putInt(0, value);
                appendByteBuf.limit(Integer.BYTES);

                final int written = appender.write(appendByteBuf);
                assert written == Integer.BYTES;

                capacity += written;
                assert capacity <= maxCapacity;
            }
            catch (IOException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return writable;
    }

    public void freeze()
    {
        try
        {
            if (appender != null)
            {
                appender.close();
                maxCapacity = capacity;
            }
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    public void delete()
    {
        try
        {
            Files.deleteIfExists(location);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    public boolean empty()
    {
        return capacity == 0;
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s (%d)", getClass().getSimpleName(), location.getFileName(), capacity);
    }

    @Override
    public void close()
    {
        IoUtil.unmap(mappedByteBuf);
    }

    private static MappedByteBuffer mapCreateAppend(
        Path file,
        int capacity)
    {
        MappedByteBuffer mapped = null;

        IoUtil.delete(file.toFile(), true);

        try (FileChannel channel = FileChannel.open(file, CREATE_NEW, READ, WRITE))
        {
            mapped = channel.map(MapMode.READ_WRITE, 0, capacity);
            channel.truncate(0L);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        assert mapped != null;
        return mapped;
    }

    private static MappedByteBuffer mapReadWrite(
        Path file)
    {
        MappedByteBuffer mapped = null;

        try (FileChannel channel = FileChannel.open(file, READ, WRITE))
        {
            mapped = channel.map(MapMode.READ_WRITE, 0, channel.size());
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        assert mapped != null;
        return mapped;
    }

    private static FileChannel openAppender(
        Path file)
    {
        FileChannel channel = null;

        try
        {
            channel = FileChannel.open(file, APPEND);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        assert channel != null;
        return channel;
    }

    public static final class Log extends KafkaCacheFile
    {
        public Log(
            Path location,
            long baseOffset,
            int capacity,
            MutableDirectBuffer appendBuf)
        {
            super(location.resolve(String.format(FORMAT_LOG_FILE, baseOffset)), capacity, appendBuf);
        }

        public Log(
            Path location,
            long baseOffset)
        {
            super(location.resolve(String.format(FORMAT_LOG_FILE, baseOffset)));
        }
    }

    public static final class Index extends KafkaCacheIndexFile.SortedByKey
    {
        public Index(
            Path location,
            long baseOffset,
            int capacity,
            MutableDirectBuffer appendBuf)
        {
            super(location.resolve(String.format(FORMAT_INDEX_FILE, baseOffset)), capacity, appendBuf);
        }

        public Index(
            Path location,
            long baseOffset)
        {
            super(location.resolve(String.format(FORMAT_INDEX_FILE, baseOffset)));
        }
    }

    public static final class HashScan extends KafkaCacheIndexFile.SortedByValue
    {
        public HashScan(
            Path location,
            long baseOffset,
            int capacity,
            MutableDirectBuffer appendBuf,
            IntFunction<long[]> sortSpaceRef)
        {
            super(location.resolve(String.format(FORMAT_HSCAN_FILE, baseOffset)), capacity, appendBuf, sortSpaceRef);
        }

        @Override
        public void freeze()
        {
            super.freeze();

            final Path hscan = location();
            final String filename = hscan.getFileName().toString();
            final Path hscanWork = hscan.resolveSibling(filename.replace(EXT_HSCAN, EXT_HSCAN_WORK));
            final Path hindex = hscan.resolveSibling(filename.replace(EXT_HSCAN, EXT_HINDEX));

            sortByKey(hscanWork, hindex);
        }
    }

    public static final class HashIndex extends KafkaCacheIndexFile.SortedByKey
    {
        public HashIndex(
            Path location,
            long baseOffset)
        {
            super(location.resolve(String.format(FORMAT_HINDEX_FILE, baseOffset)));
        }
    }

    public static final class KeysScan extends KafkaCacheIndexFile.SortedByValue
    {
        public KeysScan(
            Path location,
            long baseOffset,
            int capacity,
            MutableDirectBuffer appendBuf,
            IntFunction<long[]> sortSpaceRef)
        {
            super(location.resolve(String.format(FORMAT_KSCAN_FILE, baseOffset)), capacity, appendBuf, sortSpaceRef);
        }

        @Override
        public void freeze()
        {
            super.freeze();

            final Path kscan = location();
            final String filename = kscan.getFileName().toString();
            final Path kscanWork = kscan.resolveSibling(filename.replace(EXT_KSCAN, EXT_KSCAN_WORK));
            final Path kindex = kscan.resolveSibling(filename.replace(EXT_KSCAN, EXT_KINDEX));

            sortByKeyUnique(kscanWork, kindex);
        }
    }

    public static final class KeysIndex extends KafkaCacheIndexFile.SortedByKey
    {
        public KeysIndex(
            Path location,
            long baseOffset)
        {
            super(location.resolve(String.format(FORMAT_KINDEX_FILE, baseOffset)));
        }
    }

    public static final class NullsScan extends KafkaCacheIndexFile.SortedByValue
    {
        public NullsScan(
            Path location,
            long baseOffset,
            int capacity,
            MutableDirectBuffer appendBuf,
            IntFunction<long[]> sortSpaceRef)
        {
            super(location.resolve(String.format(FORMAT_NSCAN_FILE, baseOffset)), capacity, appendBuf, sortSpaceRef);
        }

        @Override
        public void freeze()
        {
            super.freeze();

            final Path nscan = location();
            final String filename = nscan.getFileName().toString();
            final Path nscanWork = nscan.resolveSibling(filename.replace(EXT_NSCAN, EXT_NSCAN_WORK));
            final Path nindex = nscan.resolveSibling(filename.replace(EXT_NSCAN, EXT_NINDEX));

            sortByKeyUnique(nscanWork, nindex);
        }
    }

    public static final class NullsIndex extends KafkaCacheIndexFile.SortedByKey
    {
        public NullsIndex(
            Path location,
            long baseOffset)
        {
            super(location.resolve(String.format(FORMAT_NINDEX_FILE, baseOffset)));
        }
    }

    public static final class Delta extends KafkaCacheFile
    {
        public Delta(
            Path location,
            long baseOffset,
            int capacity,
            MutableDirectBuffer appendBuf)
        {
            super(location.resolve(String.format(FORMAT_DELTA_FILE, baseOffset)), capacity, appendBuf);
        }

        public Delta(
            Path location,
            long baseOffset)
        {
            super(location.resolve(String.format(FORMAT_DELTA_FILE, baseOffset)));
        }
    }
}
