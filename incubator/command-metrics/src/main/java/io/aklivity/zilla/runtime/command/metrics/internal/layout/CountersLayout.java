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
import java.util.function.LongSupplier;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class CountersLayout implements Iterable<LongSupplier[]>
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

    public CountersIterator iterator()
    {
        return new CountersIterator();
    }

    public final class CountersIterator implements Iterator<LongSupplier[]>
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
        public LongSupplier[] next()
        {
            long bindingId = buffer.getLong(index + BINDING_ID_OFFSET);
            long metricId = buffer.getLong(index + METRIC_ID_OFFSET);
            long value = buffer.getLong(index + VALUE_OFFSET);
            index += RECORD_SIZE;
            return new LongSupplier[]{() -> bindingId, () -> metricId, () -> value};
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

        public CountersLayout build()
        {
            final File layoutFile = path.toFile();
            final MappedByteBuffer mappedBuffer = mapExistingFile(layoutFile, "counters");
            final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
            return new CountersLayout(atomicBuffer);
        }
    }
}
