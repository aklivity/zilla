/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.metrics.internal.layout;

import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class GaugesLayout extends MetricsLayout
{
    // We use the buffer to store structs {long bindingId, long metricId, long value}
    private static final int RECORD_SIZE = 3 * FIELD_SIZE;

    private GaugesLayout(
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
        return delta -> buffer.getAndAddLong(index + VALUE_OFFSET, delta);
    }

    @Override
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

    @Override
    public LongSupplier[] supplyReaders(
        long bindingId,
        long metricId)
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    void createRecord(
        long bindingId,
        long metricId,
        int index)
    {
        buffer.putLong(index + BINDING_ID_OFFSET, bindingId);
        buffer.putLong(index + METRIC_ID_OFFSET, metricId);
        buffer.putLong(index + VALUE_OFFSET, 0L); // initial value
    }

    @Override
    int recordSize()
    {
        return RECORD_SIZE;
    }

    public static final class Builder extends Layout.Builder<GaugesLayout>
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

        @Override
        public GaugesLayout build()
        {
            final File layoutFile = path.toFile();
            if (mode == Mode.CREATE_READ_WRITE)
            {
                CloseHelper.close(createEmptyFile(layoutFile, capacity));
            }
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "gauges");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new GaugesLayout(atomicBuffer);
        }
    }
}
