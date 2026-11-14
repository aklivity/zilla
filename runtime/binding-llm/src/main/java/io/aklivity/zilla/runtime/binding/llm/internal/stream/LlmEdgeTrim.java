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
 * The caller keeps the state, which is {@link #BEFORE} until the first byte, {@link #NONE} while no byte is
 * withheld, and otherwise the withheld byte value, so a value in flight costs one int.
 */
final class LlmEdgeTrim
{
    static final int BEFORE = -2;
    static final int NONE = -1;

    private static final DirectBufferEx BYTE_VALUES = byteValues();

    static int put(
        int held,
        LlmSectionPipeline.Output target,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        int next = held;
        int from = offset;
        final int to = offset + length;

        if (next == BEFORE && to > from)
        {
            from++;
            next = NONE;
        }

        if (to > from)
        {
            if (next != BEFORE && next != NONE)
            {
                target.put(BYTE_VALUES, next, 1, false);
            }

            if (to - from > 1)
            {
                target.put(buffer, from, to - from - 1, false);
            }

            next = buffer.getByte(to - 1) & 0xFF;
        }

        if (last)
        {
            next = BEFORE;
            target.put(buffer, offset, 0, true);
        }

        return next;
    }

    private static DirectBufferEx byteValues()
    {
        final byte[] values = new byte[256];

        for (int value = 0; value < values.length; value++)
        {
            values[value] = (byte) value;
        }

        return new UnsafeBufferEx(values);
    }

    private LlmEdgeTrim()
    {
    }
}
