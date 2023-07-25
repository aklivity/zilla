/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.layouts.metrics;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class HistogramsLayout extends MetricsLayout
{
    public static final int BUCKETS = 63;
    public static final long[] BUCKET_LIMITS = generateBucketLimits();

    // We use the buffer to store structs {long bindingId, long metricId, long[] values}
    private static final int VALUES_OFFSET = 2 * FIELD_SIZE;
    private static final int ARRAY_SIZE = BUCKETS * FIELD_SIZE;
    private static final int RECORD_SIZE = 2 * FIELD_SIZE + ARRAY_SIZE;
    private static final LongSupplier ZERO_LONG_SUPPLIER = () -> 0L;

    private HistogramsLayout(
        AtomicBuffer buffer)
    {
        super(buffer);
    }

    @Override
    public LongConsumer supplyWriter(
        long bindingId,
        long metricId)
    {
        int index = findOrSetPosition(bindingId, metricId);
        return value -> buffer.getAndAddLong(index + VALUES_OFFSET + findBucket(value) * FIELD_SIZE, 1);
    }

    @Override
    public LongSupplier supplyReader(
        long bindingId,
        long metricId)
    {
        throw new RuntimeException("not implemented");
    }

    @Override
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

    private int findBucket(
        long value)
    {
        assert value >= 0;
        return Math.max(63 - Long.numberOfLeadingZeros(value), 0);
    }

    private LongSupplier newLongSupplier(
        int index)
    {
        return () -> buffer.getLong(index);
    }

    @Override
    protected void createRecord(
        long bindingId,
        long metricId,
        int index)
    {
        buffer.putLong(index + BINDING_ID_OFFSET, bindingId);
        buffer.putLong(index + METRIC_ID_OFFSET, metricId);
        ByteBuffer initialValues = ByteBuffer.allocate(ARRAY_SIZE); // all zeroes
        buffer.putBytes(index + VALUES_OFFSET, initialValues.array());
    }

    @Override
    protected int recordSize()
    {
        return RECORD_SIZE;
    }

    // exclusive upper limits of each bucket
    private static long[] generateBucketLimits()
    {
        long[] limits = new long[BUCKETS];
        for (int i = 0; i < BUCKETS; i++)
        {
            limits[i] = 1L << (i + 1);
        }
        return limits;
    }

    public static final class Builder
    {
        public static final String HISTOGRAMS_LABEL = "histograms";

        private long capacity;
        private Path path;
        private boolean readonly;

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

        public Builder readonly(
            boolean readonly)
        {
            this.readonly = readonly;
            return this;
        }

        public HistogramsLayout build()
        {
            final File layoutFile = path.toFile();
            if (!readonly)
            {
                CloseHelper.close(createEmptyFile(layoutFile, capacity));
            }
            FileChannel.MapMode mode = readonly ? READ_ONLY : READ_WRITE;
            MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, mode, HISTOGRAMS_LABEL);
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new HistogramsLayout(atomicBuffer);
        }
    }
}
