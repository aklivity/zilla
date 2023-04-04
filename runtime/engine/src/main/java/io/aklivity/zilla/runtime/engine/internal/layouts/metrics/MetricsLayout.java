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
package io.aklivity.zilla.runtime.engine.internal.layouts.metrics;

import static org.agrona.IoUtil.unmap;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;

import io.aklivity.zilla.runtime.engine.internal.layouts.Layout;

abstract class MetricsLayout extends Layout
{
    static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    static final int BINDING_ID_OFFSET = 0;
    static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    static final int VALUE_OFFSET = 2 * FIELD_SIZE;
    static final int NOT_FOUND = -1;

    final AtomicBuffer buffer;

    MetricsLayout(
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

    int findPosition(
        long bindingId,
        long metricId)
    {
        // find position or return -1 if not found
        return findPosition(bindingId, metricId, false);
    }

    int findOrSetPosition(
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
                i += recordSize();
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

    abstract void createRecord(
        long bindingId,
        long metricId,
        int index);

    abstract int recordSize();

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
