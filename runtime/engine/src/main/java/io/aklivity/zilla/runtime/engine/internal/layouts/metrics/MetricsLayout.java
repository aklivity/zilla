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

import static org.agrona.IoUtil.unmap;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.stream.StreamSupport;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;

public abstract class MetricsLayout implements AutoCloseable
{
    // Record key: long bindingId (8 bytes) + int metricId (4 bytes) + int attributesId (4 bytes)
    protected static final int BINDING_ID_OFFSET = 0;
    protected static final int METRIC_ID_OFFSET = BitUtil.SIZE_OF_LONG;
    protected static final int ATTRIBUTES_ID_OFFSET = BitUtil.SIZE_OF_LONG + BitUtil.SIZE_OF_INT;
    protected static final int VALUE_OFFSET = BitUtil.SIZE_OF_LONG + 2 * BitUtil.SIZE_OF_INT;
    protected static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
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
        int metricId,
        int attributesId)
    {
        return findPosition(bindingId, metricId, attributesId, false);
    }

    protected int findOrSetPosition(
        long bindingId,
        int metricId,
        int attributesId)
    {
        return findPosition(bindingId, metricId, attributesId, true);
    }

    private int findPosition(
        long bindingId,
        int metricId,
        int attributesId,
        boolean createIfEmpty)
    {
        int pos = 0;
        boolean done = false;
        while (!done)
        {
            long b = buffer.getLong(pos + BINDING_ID_OFFSET);
            int m = buffer.getInt(pos + METRIC_ID_OFFSET);
            int a = buffer.getInt(pos + ATTRIBUTES_ID_OFFSET);
            if (b == bindingId && m == metricId && a == attributesId)
            {
                done = true;
            }
            else if (isEmptySlot(m))
            {
                if (createIfEmpty)
                {
                    createRecord(bindingId, metricId, attributesId, pos);
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
        int metricId)
    {
        return metricId == 0;
    }

    public abstract LongConsumer supplyWriter(
        long bindingId,
        int metricId,
        int attributesId);

    public abstract LongSupplier supplyReader(
        long bindingId,
        int metricId,
        int attributesId);

    public abstract LongSupplier[] supplyReaders(
        long bindingId,
        int metricId,
        int attributesId);

    protected abstract void createRecord(
        long bindingId,
        int metricId,
        int attributesId,
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
            return buffer.getInt(index + METRIC_ID_OFFSET) != 0;
        }

        @Override
        public long[] next()
        {
            long bindingId = buffer.getLong(index + BINDING_ID_OFFSET);
            long metricId = buffer.getInt(index + METRIC_ID_OFFSET);
            long attributesId = buffer.getInt(index + ATTRIBUTES_ID_OFFSET);
            index += recordSize();
            return new long[]{bindingId, metricId, attributesId};
        }
    }
}
