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
package io.aklivity.zilla.runtime.command.metrics.internal.layout;

import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.stream.StreamSupport;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class CountersLayout
{
    // We use the buffer to store structs {long bindingId, long metricId, long value}
    private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int RECORD_SIZE = 3 * FIELD_SIZE;
    private static final int BINDING_ID_OFFSET = 0;
    private static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    private static final int VALUE_OFFSET = 2 * FIELD_SIZE;

    private final AtomicBuffer buffer;

    private CountersLayout(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;
    }

    public void close()
    {
        unmap(buffer.byteBuffer());
    }

    public LongConsumer supplyWriter(
        long bindingId,
        long metricId)
    {
        int index = findOrSetPosition(bindingId, metricId);
        return delta -> buffer.getAndAddLong(index + VALUE_OFFSET, delta);
    }

    public LongSupplier supplyReader(
        long bindingId,
        long metricId)
    {
        int index = findPosition(bindingId, metricId);
        LongSupplier reader;
        if (index == -1) // not found
        {
            reader = () -> 0L;
        }
        else
        {
            reader = () -> buffer.getLong(index + VALUE_OFFSET);
        }
        return reader;
    }

    public long[][] getIds()
    {
        Spliterator<long[]> spliterator = Spliterators.spliteratorUnknownSize(new CountersIterator(), 0);
        return StreamSupport.stream(spliterator, false).toArray(long[][]::new);
    }

    private int findPosition(
        long bindingId,
        long metricId)
    {
        // find position or return -1 if not found
        return findPosition(bindingId, metricId, false);
    }

    private int findOrSetPosition(
        long bindingId,
        long metricId)
    {
        // find position or create slot if not found
        return findPosition(bindingId, metricId, true);
    }

    private int findPosition(
        long bindingId,
        long metricId,
        boolean create)
    {
        int i = 0;
        boolean done = false;
        while (!done)
        {
            long b = buffer.getLong(i + BINDING_ID_OFFSET);
            long m = buffer.getLong(i + METRIC_ID_OFFSET);
            if (b == bindingId && m == metricId)
            {
                done = true;
            }
            else if (b == 0L && m == 0L)
            {
                // we reached and empty slot, which means we did not find the proper record
                if (create)
                {
                    // let's create it
                    buffer.putLong(i + BINDING_ID_OFFSET, bindingId);
                    buffer.putLong(i + METRIC_ID_OFFSET, metricId);
                    buffer.putLong(i + VALUE_OFFSET, 0L); // initial value
                }
                else
                {
                    // let's return the error code
                    i = -1;
                }
                done = true;
            }
            else
            {
                i += RECORD_SIZE;
            }
        }
        return i;
    }

    private final class CountersIterator implements Iterator<long[]>
    {
        private int index = 0;

        @Override
        public boolean hasNext()
        {
            return isBufferLeft() && isRecordLeft();
        }

        private boolean isBufferLeft()
        {
            return index < buffer.capacity();
        }

        private boolean isRecordLeft()
        {
            return buffer.getLong(index + BINDING_ID_OFFSET) != 0L;
        }

        @Override
        public long[] next()
        {
            long bindingId = buffer.getLong(index + BINDING_ID_OFFSET);
            long metricId = buffer.getLong(index + METRIC_ID_OFFSET);
            index += RECORD_SIZE;
            return new long[]{bindingId, metricId};
        }
    }

    public static final class Builder
    {
        private long capacity;
        private Path path;

        public Builder capacity(
            long capacity)
        {
            this.capacity = capacity;
            return this;
        }

        public Builder path(
            Path path)
        {
            this.path = path;
            return this;
        }

        public CountersLayout build()
        {
            final File layoutFile = path.toFile();
            //CloseHelper.close(createEmptyFile(layoutFile, capacity)); // TODO: Ati
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "counters");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new CountersLayout(atomicBuffer);
        }
    }
}
