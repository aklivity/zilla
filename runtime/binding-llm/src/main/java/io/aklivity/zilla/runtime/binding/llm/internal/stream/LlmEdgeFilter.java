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
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * Forwards a serialized value without its first and last bytes, such as the braces of an object or the quotes of a string.
 * <p>
 * The leading byte is dropped from the first byte received, and the trailing byte is dropped by
 * withholding the most recent byte until either more bytes arrive or the last chunk is reported.
 */
final class LlmEdgeFilter implements LlmSectionPipeline.Output
{
    private final LlmSectionPipeline.Output delegate;
    private final byte[] withheld;
    private final UnsafeBufferEx withheldBuffer;

    private boolean started;
    private boolean holding;

    LlmEdgeFilter(
        LlmSectionPipeline.Output delegate)
    {
        this.delegate = delegate;
        this.withheld = new byte[1];
        this.withheldBuffer = new UnsafeBufferEx(withheld);
    }

    void reset()
    {
        started = false;
        holding = false;
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

        if (to > from)
        {
            if (holding)
            {
                delegate.put(withheldBuffer, 0, 1, false);
            }

            if (to - from > 1)
            {
                delegate.put(buffer, from, to - from - 1, false);
            }

            withheld[0] = buffer.getByte(to - 1);
            holding = true;
        }

        if (last)
        {
            holding = false;
            started = false;
            delegate.put(buffer, offset, 0, true);
        }
    }
}
