/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated;

import static org.agrona.IoUtil.unmap;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.stream.StreamSupport;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;

public abstract class MetricsLayout extends Layout
{
    protected static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    protected static final int BINDING_ID_OFFSET = 0;
    protected static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    protected static final int VALUE_OFFSET = 2 * FIELD_SIZE;
    protected static final int NOT_FOUND = -1;

    protected final AtomicBuffer buffer;

    protected MetricsLayout(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;
    }

    @Override
    public void close()
    {
        unmap(buffer.byteBuffer());
    }

    public long[][] getIds()
    {
        Spliterator<long[]> spliterator = Spliterators.spliteratorUnknownSize(new MetricsIterator(), 0);
        return StreamSupport.stream(spliterator, false).toArray(long[][]::new);
    }

    protected int findPosition(
         long bindingId,
         long metricId)
    {
        // find position or return -1 if not found
        return findPosition(bindingId, metricId, false);
    }

    protected int findOrSetPosition(
        long bindingId,
        long metricId)
    {
        // find position or create slot if not found
        return findPosition(bindingId, metricId, true);
    }

    private int findPosition(
        long bindingId,
        long metricId,
        boolean createIfEmpty)
    {
        int pos = 0;
        boolean done = false;
        while (!done)
        {
            long b = buffer.getLong(pos + BINDING_ID_OFFSET);
            long m = buffer.getLong(pos + METRIC_ID_OFFSET);
            if (b == bindingId && m == metricId)
            {
                done = true;
            }
            else if (isEmptySlot(b, m))
            {
                if (createIfEmpty)
                {
                    createRecord(bindingId, metricId, pos);
                }
                else
                {
                    pos = NOT_FOUND;
                }
                done = true;
            }
            else
            {
                pos += recordSize();
            }
        }
        return pos;
    }

    private boolean isEmptySlot(
        long bindingId,
        long metricId)
    {
        return bindingId == 0L && metricId == 0L;
    }

    public abstract LongConsumer supplyWriter(
        long bindingId,
        long metricId);

    public abstract LongSupplier supplyReader(
        long bindingId,
        long metricId);

    public abstract LongSupplier[] supplyReaders(
        long bindingId,
        long metricId);

    protected abstract void createRecord(
        long bindingId,
        long metricId,
        int index);

    protected abstract int recordSize();

    final class MetricsIterator implements Iterator<long[]>
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
            index += recordSize();
            return new long[]{bindingId, metricId};
        }
    }
}
