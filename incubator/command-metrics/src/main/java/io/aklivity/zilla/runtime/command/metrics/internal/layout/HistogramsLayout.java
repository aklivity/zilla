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

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.HISTOGRAM_BUCKETS;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class HistogramsLayout implements Iterable<LongSupplier[]>
{
    // We use the buffer to store structs {long bindingId, long metricId, long[] values}
    /*private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int SCALAR_FIELDS = 2;
    private static final int ARRAY_SIZE = HISTOGRAM_BUCKETS * FIELD_SIZE;
    private static final int RECORD_SIZE = SCALAR_FIELDS * FIELD_SIZE + ARRAY_SIZE;*/

    // We use the buffer to store structs {long bindingId, long metricId, long[] values}
    private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int BINDING_ID_OFFSET = 0;
    private static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    private static final int VALUES_OFFSET = 2 * FIELD_SIZE;
    private static final int BUCKETS = 63;
    private static final int ARRAY_SIZE = BUCKETS * FIELD_SIZE;
    private static final int RECORD_SIZE = 2 * FIELD_SIZE + ARRAY_SIZE;
    private static final LongSupplier ZERO_LONG_SUPPLIER = () -> 0L;

    private final AtomicBuffer buffer;

    private HistogramsLayout(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;
    }

    public LongSupplier[] supplyReaders(
            long bindingId,
            long metricId)
    {
        LongSupplier[] readers;
        int index = findPosition(bindingId, metricId);
        if (index == -1) // not found
        {
            readers = IntStream.range(0, BUCKETS)
                    .mapToObj(bucket -> ZERO_LONG_SUPPLIER)
                    .collect(Collectors.toList())
                    .toArray(LongSupplier[]::new);
        }
        else
        {
            readers = IntStream.range(0, BUCKETS)
                    .mapToObj(bucket -> newLongSupplier(index + VALUES_OFFSET + bucket * FIELD_SIZE))
                    .collect(Collectors.toList())
                    .toArray(LongSupplier[]::new);
        }
        return readers;
    }

    private LongSupplier newLongSupplier(int i)
    {
        return () -> buffer.getLong(i);
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
                    ByteBuffer initialValues = ByteBuffer.allocate(ARRAY_SIZE); // all zeroes
                    buffer.putBytes(i + VALUES_OFFSET, initialValues.array());
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

    public void close()
    {
        unmap(buffer.byteBuffer());
    }

    public HistogramsIterator iterator()
    {
        return new HistogramsIterator();
    }

    public final class HistogramsIterator implements Iterator<LongSupplier[]>
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
            return buffer.getLong(index) != 0L;
        }

        @Override
        public LongSupplier[] next()
        {
            LongSupplier[] readers = IntStream.range(0, 2 * FIELD_SIZE + HISTOGRAM_BUCKETS)
                    .mapToObj(offset -> newLongSupplier(index + offset * FIELD_SIZE))
                    .collect(Collectors.toList())
                    .toArray(LongSupplier[]::new);
            index += RECORD_SIZE;
            return readers;
        }

        private LongSupplier newLongSupplier(int i)
        {
            return () -> buffer.getLong(i);
        }
    }

    public static final class Builder
    {
        private Path path;

        public Builder path(
            Path path)
        {
            this.path = path;
            return this;
        }

        public HistogramsLayout build()
        {
            final File layoutFile = path.toFile();
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "histograms");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new HistogramsLayout(atomicBuffer);
        }
    }
}
