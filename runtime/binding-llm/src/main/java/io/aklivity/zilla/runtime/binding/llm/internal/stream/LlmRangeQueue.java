/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import java.util.Arrays;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.llm.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAttributeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmRangeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionDataExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

// The ranges received for the frames of a section, waiting to be laid over the frames that carry the same bytes
// onward. A range is kept as the part it arrived as, so that a range split across frames keeps its attributes for
// its closing part. Once more bytes are pending than a frame could carry ranges for, the section's ranges are
// dropped.
final class LlmRangeQueue
{
    private static final int RANGE_OVERHEAD = 16;
    private static final int INITIAL_ENTRIES = 16;
    private static final int INITIAL_STORAGE = 1024;

    private final int budget;
    private final Consumer<LlmRangeFW> appender;
    private final Consumer<LlmRangeFW.Builder> writer;
    private final Array32FW<LlmAttributeFW> attributesRO;
    private final UnsafeBufferEx storageRO;

    private int[] remaining;
    private boolean[] closing;
    private int[] attributesOffset;
    private int[] attributesLength;
    private byte[] storage;
    private int storageLimit;
    private int head;
    private int tail;
    private int pending;
    private boolean dropped;
    private int writeLength;
    private boolean writeClosing;
    private int writeEntry;

    LlmRangeQueue(
        int budget)
    {
        this.budget = budget;
        this.appender = this::append;
        this.writer = this::write;
        this.attributesRO = new Array32FW<>(new LlmAttributeFW());
        this.storageRO = new UnsafeBufferEx();
        this.remaining = new int[INITIAL_ENTRIES];
        this.closing = new boolean[INITIAL_ENTRIES];
        this.attributesOffset = new int[INITIAL_ENTRIES];
        this.attributesLength = new int[INITIAL_ENTRIES];
        this.storage = new byte[INITIAL_STORAGE];
    }

    void begin()
    {
        head = 0;
        tail = 0;
        storageLimit = 0;
        pending = 0;
        dropped = false;
    }

    void accept(
        Array32FW<LlmRangeFW> ranges)
    {
        if (!dropped)
        {
            ranges.forEach(appender);

            if (pending > budget)
            {
                drop();
            }
        }
    }

    void drop()
    {
        head = 0;
        tail = 0;
        storageLimit = 0;
        pending = 0;
        dropped = true;
    }

    void emitComplete(
        LlmSectionDataExFW.Builder section,
        int values)
    {
        int unwritten = values;
        int carry = 0;

        while (head < tail && remaining[head] <= unwritten)
        {
            unwritten -= remaining[head];

            if (closing[head])
            {
                writeLength = carry + remaining[head];
                writeClosing = true;
                writeEntry = head;
                section.rangesItem(writer);
                carry = 0;
            }
            else
            {
                carry += remaining[head];
            }

            remaining[head] = 0;
            release();
        }
    }

    void emit(
        LlmSectionDataExFW.Builder section,
        int values)
    {
        int unwritten = values;
        int carry = 0;

        while (head < tail && (unwritten > 0 || remaining[head] == 0))
        {
            final int take = Math.min(unwritten, remaining[head]);

            unwritten -= take;
            remaining[head] -= take;

            if (remaining[head] > 0)
            {
                carry += take;
            }
            else if (closing[head])
            {
                writeLength = carry + take;
                writeClosing = true;
                writeEntry = head;
                section.rangesItem(writer);
                carry = 0;
                release();
            }
            else
            {
                carry += take;
                release();
            }
        }

        if (carry > 0)
        {
            writeLength = carry;
            writeClosing = false;
            writeEntry = -1;
            section.rangesItem(writer);
        }

        if (head == tail)
        {
            head = 0;
            tail = 0;
            storageLimit = 0;
            pending = 0;
        }
    }

    private void release()
    {
        pending -= attributesLength[head] + RANGE_OVERHEAD;
        head++;
    }

    private void append(
        LlmRangeFW range)
    {
        final Array32FW<LlmAttributeFW> attributes = range.attributes();
        final int length = attributes.sizeof();

        if (tail == remaining.length)
        {
            final int capacity = tail << 1;

            remaining = Arrays.copyOf(remaining, capacity);
            closing = Arrays.copyOf(closing, capacity);
            attributesOffset = Arrays.copyOf(attributesOffset, capacity);
            attributesLength = Arrays.copyOf(attributesLength, capacity);
        }

        if (storageLimit + length > storage.length)
        {
            storage = Arrays.copyOf(storage, Math.max(storage.length << 1, storageLimit + length));
        }

        attributes.buffer().getBytes(attributes.offset(), storage, storageLimit, length);

        remaining[tail] = range.length();
        closing[tail] = range.closing() != 0;
        attributesOffset[tail] = storageLimit;
        attributesLength[tail] = length;
        storageLimit += length;
        pending += length + RANGE_OVERHEAD;
        tail++;
    }

    private void write(
        LlmRangeFW.Builder range)
    {
        range.length(writeLength).closing(writeClosing ? 1 : 0);

        if (writeEntry >= 0)
        {
            storageRO.wrap(storage, 0, storageLimit);
            range.attributes(attributesRO.wrap(storageRO, attributesOffset[writeEntry], storageLimit));
        }
    }
}
