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

import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class HistogramsLayout extends Layout
{
    public static final int BUCKETS = 63;
    public static final Map<Integer, Long> BUCKET_LIMITS = generateBucketLimits();

    // We use the buffer to store structs {long bindingId, long metricId, long[] values}
    private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int BINDING_ID_OFFSET = 0;
    private static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    private static final int VALUES_OFFSET = 2 * FIELD_SIZE;
    private static final int ARRAY_SIZE = BUCKETS * FIELD_SIZE;
    private static final int RECORD_SIZE = 2 * FIELD_SIZE + ARRAY_SIZE;
    private static final LongSupplier ZERO_LONG_SUPPLIER = () -> 0L;
    private static final int NOT_FOUND = -1;

    private final AtomicBuffer buffer;

    private HistogramsLayout(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;
    }

    @Override
    public void close()
    {
        unmap(buffer.byteBuffer());
    }

    public LongConsumer supplyWriter(
        long bindingId,
        long metricId)
    {
        int index = findOrSetPosition(bindingId, metricId);
        return value ->
        {
            System.out.format("HistogramLayout write: bnd=%d met=%d val=%d bck=%d\n",
                    bindingId, metricId, value, findBucket(value)); // TODO: Ati
            buffer.getAndAddLong(index + VALUES_OFFSET + findBucket(value) * FIELD_SIZE, 1);
        };
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

    public long[][] getIds()
    {
        Spliterator<long[]> spliterator = Spliterators.spliteratorUnknownSize(new HistogramsIterator(), 0);
        return StreamSupport.stream(spliterator, false).toArray(long[][]::new);
    }

    private int findBucket(
        long value)
    {
        assert value >= 0;
        return Math.max(63 - Long.numberOfLeadingZeros(value), 0);
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
            else if (isEmptySlot(b, m))
            {
                if (create)
                {
                    createRecord(bindingId, metricId, i);
                }
                else
                {
                    i = NOT_FOUND;
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

    private boolean isEmptySlot(
        long bindingId,
        long metricId)
    {
        return bindingId == 0L && metricId == 0L;
    }

    private void createRecord(
        long bindingId,
        long metricId,
        int index)
    {
        buffer.putLong(index + BINDING_ID_OFFSET, bindingId);
        buffer.putLong(index + METRIC_ID_OFFSET, metricId);
        ByteBuffer initialValues = ByteBuffer.allocate(ARRAY_SIZE); // all zeroes
        buffer.putBytes(index + VALUES_OFFSET, initialValues.array());
    }

    // exclusive upper limits of each bucket
    private static Map<Integer, Long> generateBucketLimits()
    {
        Map<Integer, Long> limits = new HashMap<>();
        for (int i = 0; i < BUCKETS; i++)
        {
            limits.put(i, 1L << (i + 1));
        }
        return limits;
    }

    private final class HistogramsIterator implements Iterator<long[]>
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
        public long[] next()
        {
            long bindingId = buffer.getLong(index + BINDING_ID_OFFSET);
            long metricId = buffer.getLong(index + METRIC_ID_OFFSET);
            index += RECORD_SIZE;
            return new long[]{bindingId, metricId};
        }
    }

    public static final class Builder extends Layout.Builder<HistogramsLayout>
    {
        private long capacity;
        private Path path;
        private Mode mode;

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

        public Builder mode(
            Mode mode)
        {
            this.mode = mode;
            return this;
        }

        public HistogramsLayout build()
        {
            final File layoutFile = path.toFile();
            if (mode == Mode.CREATE_READ_WRITE)
            {
                CloseHelper.close(createEmptyFile(layoutFile, capacity));
            }
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "histograms");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new HistogramsLayout(atomicBuffer);
        }
    }
}
