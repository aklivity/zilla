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
package io.aklivity.zilla.engine.drive.internal;

import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.agrona.IoUtil;
import org.agrona.LangUtil;
import org.agrona.collections.Long2LongHashMap;

public final class Tuning implements AutoCloseable
{
    private final int available;
    private final Long2LongHashMap affinities;
    private final Path tuning;

    private MappedByteBuffer mappedByteBuf;

    public Tuning(
        Path directory,
        int count)
    {
        this.available = (1 << count) - 1;
        this.affinities = new Long2LongHashMap(-1L);
        this.tuning = directory.resolve("tuning");
    }

    public void reset()
    {
        try
        {
            Files.deleteIfExists(tuning);
            Files.createDirectories(tuning.getParent());
            Files.createFile(tuning);

            mappedByteBuf = mapCreateReadWrite(tuning, 10 * 1024);
        }
        catch (IOException ex)
        {
            System.out.printf("Error: %s is not writeable\n", tuning);
        }
    }

    public void affinity(
        long routeId,
        long mask)
    {
        assert (mask & ~available) == 0 || mask == 1L << (Long.SIZE - 1);

        long offset = affinities.get(routeId);

        if (offset == affinities.missingValue())
        {
            try (SeekableByteChannel channel = Files.newByteChannel(tuning, APPEND))
            {
                offset = channel.position() + Long.BYTES;

                ByteBuffer byteBuf = ByteBuffer
                        .wrap(new byte[Long.BYTES + Long.BYTES])
                        .order(nativeOrder());
                byteBuf.putLong(routeId);
                byteBuf.putLong(0L);
                byteBuf.flip();

                while (byteBuf.hasRemaining())
                {
                    channel.write(byteBuf);
                    Thread.onSpinWait();
                }

                affinities.put(routeId, offset);
            }
            catch (IOException ex)
            {
                System.out.printf("Error: %s is not writeable\n", tuning);
            }
        }

        offset = affinities.get(routeId);
        assert offset != affinities.missingValue();

        mappedByteBuf.putLong((int) offset, mask);
    }

    public long affinity(
        long routeId)
    {
        long offset = affinities.get(routeId);

        return offset != affinities.missingValue() ? mappedByteBuf.getLong((int) offset) : available;
    }

    @Override
    public void close() throws Exception
    {
        if (mappedByteBuf != null)
        {
            IoUtil.unmap(mappedByteBuf);
        }
    }


    private static MappedByteBuffer mapCreateReadWrite(
        Path file,
        int capacity)
    {
        MappedByteBuffer mapped = null;

        IoUtil.delete(file.toFile(), true);

        try (FileChannel channel = FileChannel.open(file, CREATE_NEW, READ, WRITE))
        {
            mapped = channel.map(MapMode.READ_WRITE, 0, capacity);
            mapped.order(nativeOrder());
            channel.truncate(0L);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        assert mapped != null;
        return mapped;
    }
}
