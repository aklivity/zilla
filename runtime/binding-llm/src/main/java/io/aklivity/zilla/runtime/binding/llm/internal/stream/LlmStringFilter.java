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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Forwards the escaped content of a serialized string without its enclosing quotes.
 * <p>
 * The opening quote is dropped from the first byte received, and the content is forwarded as it arrives
 * until the first quote that is not part of an escape sequence, which closes the string.
 */
final class LlmStringFilter implements LlmSectionPipeline.Output
{
    private final LlmSectionPipeline.Output delegate;

    private boolean started;
    private boolean escaping;
    private boolean closed;

    LlmStringFilter(
        LlmSectionPipeline.Output delegate)
    {
        this.delegate = delegate;
    }

    void reset()
    {
        started = false;
        escaping = false;
        closed = false;
    }

    @Override
    public void put(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        int from = offset;
        final int to = offset + length;

        if (!started && to > from)
        {
            from++;
            started = true;
        }

        int end = from;

        while (end < to && !closed)
        {
            final byte b = buffer.getByte(end);

            if (escaping)
            {
                escaping = false;
            }
            else if (b == '\\')
            {
                escaping = true;
            }
            else if (b == '"')
            {
                closed = true;
            }

            if (!closed)
            {
                end++;
            }
        }

        if (end > from)
        {
            delegate.put(buffer, from, end - from, false);
        }

        if (last)
        {
            delegate.put(buffer, offset, 0, true);
            reset();
        }
    }
}
