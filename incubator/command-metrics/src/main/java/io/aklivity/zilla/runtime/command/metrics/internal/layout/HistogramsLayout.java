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
    private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int SCALAR_FIELDS = 2;
    private static final int ARRAY_SIZE = HISTOGRAM_BUCKETS * FIELD_SIZE;
    private static final int RECORD_SIZE = SCALAR_FIELDS * FIELD_SIZE + ARRAY_SIZE;

    private final AtomicBuffer buffer;

    private HistogramsLayout(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;
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
            LongSupplier[] readers = IntStream.range(0, SCALAR_FIELDS + HISTOGRAM_BUCKETS)
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
