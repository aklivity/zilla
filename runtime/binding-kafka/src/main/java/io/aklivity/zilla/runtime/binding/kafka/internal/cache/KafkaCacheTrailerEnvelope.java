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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import java.util.Arrays;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

// A write-collecting ModelEnvelope for the producer-encode path: every set() call is appended, in
// call order, to an arena keyed by (name, value) offsets -- repeated names simply add further
// entries rather than overwriting, so writeHeaders() below reproduces them as that many repeated
// Kafka headers, in the same order they were set. reset() clears the arena so one instance is
// reused across every message on a stream, one entry set at a time.
public final class KafkaCacheTrailerEnvelope implements ModelEnvelope
{
    private static final int INITIAL_CAPACITY = 8;

    private final MutableDirectBufferEx arena = new ExpandableArrayBufferEx();
    private final MutableDirectBufferEx queryBuffer = new UnsafeBufferEx(new byte[256]);
    private final DirectBufferEx queryView = new UnsafeBufferEx(new byte[0]);
    private final DirectBufferEx storedNameView = new UnsafeBufferEx(new byte[0]);
    private final DirectBufferEx storedValueView = new UnsafeBufferEx(new byte[0]);

    private int[] nameOffsets = new int[INITIAL_CAPACITY];
    private int[] nameLengths = new int[INITIAL_CAPACITY];
    private int[] valueOffsets = new int[INITIAL_CAPACITY];
    private int[] valueLengths = new int[INITIAL_CAPACITY];

    private int entryCount;
    private int arenaLength;
    private int queryLength;

    public void reset()
    {
        entryCount = 0;
        arenaLength = 0;
    }

    public boolean isEmpty()
    {
        return entryCount == 0;
    }

    public void writeHeaders(
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> trailers)
    {
        for (int i = 0; i < entryCount; i++)
        {
            final int nameOffset = nameOffsets[i];
            final int nameLength = nameLengths[i];
            final int valueOffset = valueOffsets[i];
            final int valueLength = valueLengths[i];
            trailers.item(h -> h.nameLen(nameLength).name(arena, nameOffset, nameLength)
                .valueLen(valueLength).value(arena, valueOffset, valueLength));
        }
    }

    @Override
    public int count(
        String name)
    {
        queryLength = queryBuffer.putStringWithoutLengthUtf8(0, name);
        queryView.wrap(queryBuffer, 0, queryLength);

        int matches = 0;
        for (int i = 0; i < entryCount; i++)
        {
            if (nameMatches(i))
            {
                matches++;
            }
        }
        return matches;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        queryLength = queryBuffer.putStringWithoutLengthUtf8(0, name);
        queryView.wrap(queryBuffer, 0, queryLength);

        DirectBufferEx value = null;
        int seen = 0;
        for (int i = 0; i < entryCount && value == null; i++)
        {
            if (nameMatches(i))
            {
                if (seen == index)
                {
                    storedValueView.wrap(arena, valueOffsets[i], valueLengths[i]);
                    value = storedValueView;
                }
                seen++;
            }
        }
        return value;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
        ensureCapacity(entryCount + 1);

        final int nameOffset = arenaLength;
        final int nameLength = arena.putStringWithoutLengthUtf8(nameOffset, name);
        arenaLength += nameLength;

        final int valueOffset = arenaLength;
        final int valueLength = value.capacity();
        arena.putBytes(valueOffset, value, 0, valueLength);
        arenaLength += valueLength;

        nameOffsets[entryCount] = nameOffset;
        nameLengths[entryCount] = nameLength;
        valueOffsets[entryCount] = valueOffset;
        valueLengths[entryCount] = valueLength;
        entryCount++;
    }

    private boolean nameMatches(
        int index)
    {
        boolean matches = false;
        if (nameLengths[index] == queryLength)
        {
            storedNameView.wrap(arena, nameOffsets[index], nameLengths[index]);
            matches = storedNameView.equals(queryView);
        }
        return matches;
    }

    private void ensureCapacity(
        int required)
    {
        if (required > nameOffsets.length)
        {
            final int newCapacity = nameOffsets.length * 2;
            nameOffsets = Arrays.copyOf(nameOffsets, newCapacity);
            nameLengths = Arrays.copyOf(nameLengths, newCapacity);
            valueOffsets = Arrays.copyOf(valueOffsets, newCapacity);
            valueLengths = Arrays.copyOf(valueLengths, newCapacity);
        }
    }
}
