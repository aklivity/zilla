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

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.util.Collection;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class LlmReasoningScanner
{
    interface Visitor
    {
        void text(
            DirectBufferEx buffer,
            int offset,
            int length);

        void reasoningBegin(
            String tag);

        void reasoning(
            DirectBufferEx buffer,
            int offset,
            int length);

        void reasoningEnd();
    }

    private static final int OUTSIDE = -1;
    private static final byte DELIMITER_START = '<';
    private static final Delimiters DEFAULT_DELIMITERS = new Delimiters(LlmReasoningTags.DEFAULTS);

    private final String[] tags;
    private final byte[][] openDelimiters;
    private final byte[][] closeDelimiters;
    private final byte[] held;
    private final DirectBufferEx heldBuffer;
    private final int minBlock;

    private int heldLength;
    private int inside;

    LlmReasoningScanner(
        Collection<String> tags)
    {
        final Delimiters delimiters = tags == LlmReasoningTags.DEFAULTS ? DEFAULT_DELIMITERS : new Delimiters(tags);

        this.tags = delimiters.tags;
        this.openDelimiters = delimiters.open;
        this.closeDelimiters = delimiters.close;
        this.minBlock = delimiters.minBlock;
        this.held = new byte[delimiters.capacity];
        this.heldBuffer = new UnsafeBufferEx(held);
        this.inside = OUTSIDE;
    }

    int minBlock()
    {
        return minBlock;
    }

    int pending()
    {
        return heldLength;
    }

    int capacity()
    {
        return held.length;
    }

    String tag()
    {
        return inside != OUTSIDE ? tags[inside] : null;
    }

    void begin()
    {
        heldLength = 0;
        inside = OUTSIDE;
    }

    void accept(
        DirectBufferEx payload,
        int offset,
        int length,
        Visitor visitor)
    {
        final int limit = offset + length;

        int run = offset;
        int index = offset;

        while (index < limit)
        {
            final byte next = payload.getByte(index);

            if (heldLength == 0 && next != DELIMITER_START)
            {
                index++;
            }
            else
            {
                emit(visitor, payload, run, index - run);
                run = absorb(visitor, next) ? index + 1 : index;
                index++;
            }
        }

        emit(visitor, payload, run, limit - run);
    }

    void flush(
        Visitor visitor)
    {
        emit(visitor, heldBuffer, 0, heldLength);
        heldLength = 0;
    }

    void end(
        Visitor visitor)
    {
        flush(visitor);

        if (inside != OUTSIDE)
        {
            visitor.reasoningEnd();
        }

        begin();
    }

    private boolean absorb(
        Visitor visitor,
        byte next)
    {
        held[heldLength] = next;

        final int length = heldLength + 1;
        int shift = 0;
        while (shift < length - 1 && candidate(shift, length) == OUTSIDE)
        {
            shift++;
        }

        final boolean absorbed = shift < length - 1 || candidate(shift, length) != OUTSIDE;
        final int released = absorbed ? shift : heldLength;

        emit(visitor, heldBuffer, 0, released);
        heldLength = absorbed ? length - shift : 0;
        System.arraycopy(held, released, held, 0, heldLength);

        if (absorbed)
        {
            final int match = candidate(0, heldLength);
            final byte[] delimiter = delimiters()[match];

            if (heldLength == delimiter.length)
            {
                heldLength = 0;

                if (inside == OUTSIDE)
                {
                    inside = match;
                    visitor.reasoningBegin(tags[match]);
                }
                else
                {
                    inside = OUTSIDE;
                    visitor.reasoningEnd();
                }
            }
        }

        return absorbed;
    }

    private int candidate(
        int from,
        int to)
    {
        final byte[][] delimiters = delimiters();
        final int first = inside == OUTSIDE ? 0 : inside;
        final int last = inside == OUTSIDE ? delimiters.length : inside + 1;

        int match = OUTSIDE;
        for (int index = first; match == OUTSIDE && index < last; index++)
        {
            final byte[] delimiter = delimiters[index];
            final int length = to - from;

            if (length <= delimiter.length)
            {
                int offset = 0;
                while (offset < length && held[from + offset] == delimiter[offset])
                {
                    offset++;
                }

                if (offset == length)
                {
                    match = index;
                }
            }
        }

        return match;
    }

    private byte[][] delimiters()
    {
        return inside == OUTSIDE ? openDelimiters : closeDelimiters;
    }

    private void emit(
        Visitor visitor,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (length > 0)
        {
            if (inside == OUTSIDE)
            {
                visitor.text(buffer, offset, length);
            }
            else
            {
                visitor.reasoning(buffer, offset, length);
            }
        }
    }

    private static final class Delimiters
    {
        private final String[] tags;
        private final byte[][] open;
        private final byte[][] close;
        private final int capacity;
        private final int minBlock;

        private Delimiters(
            Collection<String> tags)
        {
            this.tags = tags.toArray(String[]::new);
            this.open = new byte[this.tags.length][];
            this.close = new byte[this.tags.length][];

            int capacity = 0;
            int minBlock = Integer.MAX_VALUE;
            for (int index = 0; index < this.tags.length; index++)
            {
                open[index] = ("<" + this.tags[index] + ">").getBytes(US_ASCII);
                close[index] = ("</" + this.tags[index] + ">").getBytes(US_ASCII);
                capacity = Math.max(capacity, close[index].length);
                minBlock = Math.min(minBlock, open[index].length + close[index].length);
            }

            this.capacity = capacity;
            this.minBlock = minBlock;
        }
    }
}
