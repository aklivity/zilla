/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import org.agrona.collections.LongArrayList;

final class KafkaShareRanges
{
    private final LongArrayList bounds;

    KafkaShareRanges()
    {
        this.bounds = new LongArrayList();
    }

    int size()
    {
        return bounds.size() >> 1;
    }

    boolean isEmpty()
    {
        return bounds.isEmpty();
    }

    long first(
        int index)
    {
        return bounds.getLong(index << 1);
    }

    long last(
        int index)
    {
        return bounds.getLong((index << 1) + 1);
    }

    long count()
    {
        long count = 0L;
        for (int index = 0; index < size(); index++)
        {
            count += last(index) - first(index) + 1L;
        }
        return count;
    }

    boolean contains(
        long offset)
    {
        return indexOf(offset) != -1;
    }

    void add(
        long first,
        long last)
    {
        int index = 0;
        while (index < size() && last(index) + 1L < first)
        {
            index++;
        }

        long mergedFirst = first;
        long mergedLast = last;
        while (index < size() && first(index) <= last + 1L)
        {
            mergedFirst = Math.min(mergedFirst, first(index));
            mergedLast = Math.max(mergedLast, last(index));
            bounds.removeAt(index << 1);
            bounds.removeAt(index << 1);
        }

        bounds.addLong(index << 1, mergedFirst);
        bounds.addLong((index << 1) + 1, mergedLast);
    }

    void remove(
        long offset)
    {
        final int index = indexOf(offset);

        if (index != -1)
        {
            final long first = first(index);
            final long last = last(index);

            if (first == last)
            {
                bounds.removeAt(index << 1);
                bounds.removeAt(index << 1);
            }
            else if (offset == first)
            {
                bounds.setLong(index << 1, first + 1L);
            }
            else if (offset == last)
            {
                bounds.setLong((index << 1) + 1, last - 1L);
            }
            else
            {
                bounds.setLong((index << 1) + 1, offset - 1L);
                bounds.addLong((index + 1) << 1, offset + 1L);
                bounds.addLong(((index + 1) << 1) + 1, last);
            }
        }
    }

    void clear()
    {
        bounds.clear();
    }

    private int indexOf(
        long offset)
    {
        int found = -1;
        for (int index = 0; found == -1 && index < size() && first(index) <= offset; index++)
        {
            if (offset <= last(index))
            {
                found = index;
            }
        }
        return found;
    }
}
