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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ScalarsLayout extends MetricsLayout
{
    // We use the buffer to store structs {long bindingId, long metricId, long value}
    private static final int RECORD_SIZE = 3 * FIELD_SIZE;

    private ScalarsLayout(
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
    protected void createRecord(
        long bindingId,
        long metricId,
        int index)
    {
        buffer.putLong(index + BINDING_ID_OFFSET, bindingId);
        buffer.putLong(index + METRIC_ID_OFFSET, metricId);
        buffer.putLong(index + VALUE_OFFSET, 0L); // initial value
    }

    @Override
    protected int recordSize()
    {
        return RECORD_SIZE;
    }

    public static final class Builder
    {
        private long capacity;
        private Path path;
        private boolean readonly;
        private String label;

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

        public Builder label(
            String label)
        {
            this.label = label;
            return this;
        }

        public ScalarsLayout build()
        {
            final File layoutFile = path.toFile();
            if (!readonly)
            {
                CloseHelper.close(createEmptyFile(layoutFile, capacity));
            }
            FileChannel.MapMode mode = readonly ? READ_ONLY : READ_WRITE;
            MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, mode, this.label);
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new ScalarsLayout(atomicBuffer);
        }
    }
}
