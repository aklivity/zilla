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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class LlmReasoningSplitter
{
    @FunctionalInterface
    interface Visitor
    {
        void accept(
            boolean reasoning,
            DirectBufferEx buffer,
            int offset,
            int length);
    }

    private String tag;
    private DirectBufferEx openDelimiter;
    private DirectBufferEx closeDelimiter;
    private boolean active;
    private boolean inside;
    private int matched;

    void begin()
    {
        active = false;
        inside = false;
        matched = 0;
    }

    void activate(
        String tag)
    {
        if (!active)
        {
            if (!tag.equals(this.tag))
            {
                this.tag = tag;
                this.openDelimiter = new UnsafeBufferEx(("<" + tag + ">").getBytes(US_ASCII));
                this.closeDelimiter = new UnsafeBufferEx(("</" + tag + ">").getBytes(US_ASCII));
            }

            active = true;
        }
    }

    void accept(
        DirectBufferEx payload,
        int offset,
        int length,
        Visitor visitor)
    {
        if (active)
        {
            scan(payload, offset, length, visitor);
        }
        else
        {
            emit(visitor, false, payload, offset, length);
        }
    }

    void end(
        Visitor visitor)
    {
        if (active && matched > 0)
        {
            emit(visitor, inside, inside ? closeDelimiter : openDelimiter, 0, matched);
        }

        begin();
    }

    private void scan(
        DirectBufferEx payload,
        int offset,
        int length,
        Visitor visitor)
    {
        final int limit = offset + length;

        int run = offset;
        int index = offset;
        int held = 0;

        while (index < limit)
        {
            final DirectBufferEx delimiter = inside ? closeDelimiter : openDelimiter;

            if (payload.getByte(index) == delimiter.getByte(matched))
            {
                index++;
                matched++;
                held++;

                if (matched == delimiter.capacity())
                {
                    emit(visitor, inside, payload, run, index - held - run);
                    inside = !inside;
                    matched = 0;
                    held = 0;
                    run = index;
                }
            }
            else if (matched > 0)
            {
                emit(visitor, inside, delimiter, 0, matched - held);
                matched = 0;
                held = 0;
            }
            else
            {
                index++;
            }
        }

        emit(visitor, inside, payload, run, limit - held - run);
    }

    private void emit(
        Visitor visitor,
        boolean reasoning,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (length > 0)
        {
            visitor.accept(reasoning, buffer, offset, length);
        }
    }
}
