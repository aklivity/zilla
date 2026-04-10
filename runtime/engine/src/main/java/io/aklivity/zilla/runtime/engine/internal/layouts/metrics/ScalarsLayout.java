/*
 * Copyright 2021-2024 Aklivity Inc.
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
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public abstract class ScalarsLayout extends MetricsLayout
{
    // Record: long bindingId (8) + int metricId (4) + int attributesId (4) + long value (8) = 24 bytes
    private static final int RECORD_SIZE = BitUtil.SIZE_OF_LONG + 2 * BitUtil.SIZE_OF_INT + BitUtil.SIZE_OF_LONG;

    protected ScalarsLayout(
        AtomicBuffer buffer)
    {
        super(buffer);
    }

    @Override
    public abstract LongConsumer supplyWriter(
        long bindingId,
        int metricId,
        int attributesId);

    @Override
    public LongSupplier supplyReader(
        long bindingId,
        int metricId,
        int attributesId)
    {
        int index = findPosition(bindingId, metricId, attributesId);
        LongSupplier reader;
        if (index == -1)
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
        int metricId,
        int attributesId)
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    protected void createRecord(
        long bindingId,
        int metricId,
        int attributesId,
        int index)
    {
        buffer.putLong(index + BINDING_ID_OFFSET, bindingId);
        buffer.putInt(index + METRIC_ID_OFFSET, metricId);
        buffer.putInt(index + ATTRIBUTES_ID_OFFSET, attributesId);
        buffer.putLong(index + VALUE_OFFSET, 0L);
    }

    @Override
    protected int recordSize()
    {
        return RECORD_SIZE;
    }

    public abstract static class Builder
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

        protected final <T extends ScalarsLayout> T build(
            Function<AtomicBuffer, T> constructor)
        {
            final File layoutFile = path.toFile();
            if (!readonly)
            {
                CloseHelper.close(createEmptyFile(layoutFile, capacity));
            }
            FileChannel.MapMode mode = readonly ? READ_ONLY : READ_WRITE;
            MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, mode, this.label);
            final AtomicBufferEx atomicBuffer = new UnsafeBufferEx(mappedBuffer);
            return constructor.apply(atomicBuffer);
        }
    }
}
