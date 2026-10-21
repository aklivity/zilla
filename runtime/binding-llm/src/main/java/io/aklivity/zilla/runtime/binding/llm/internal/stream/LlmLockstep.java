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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

// Follows what a chain that is not identity produces against what it consumed, byte for byte, until the first byte
// that differs. The bytes consumed and not yet produced wait in the lag, so a chain that holds bytes back is still
// compared against the same position of its input. Past the first differing byte nothing matches any longer.
final class LlmLockstep
{
    private static final int INITIAL_LAG = 256;

    private final int capacity;

    private byte[] lag;
    private int lagLength;
    private boolean diverged;

    LlmLockstep(
        int capacity)
    {
        this.capacity = capacity;
        this.lag = new byte[Math.min(INITIAL_LAG, capacity)];
    }

    void begin()
    {
        lagLength = 0;
        diverged = false;
    }

    boolean diverged()
    {
        return diverged;
    }

    void consumed(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (!diverged)
        {
            if (lagLength + length > capacity)
            {
                diverged = true;
            }
            else
            {
                if (lagLength + length > lag.length)
                {
                    lag = Arrays.copyOf(lag, Math.min(Math.max(lag.length << 1, lagLength + length), capacity));
                }

                buffer.getBytes(offset, lag, lagLength, length);
                lagLength += length;
            }
        }
    }

    int produced(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        int matched = 0;

        if (!diverged)
        {
            final int comparable = Math.min(length, lagLength);

            while (matched < comparable && buffer.getByte(offset + matched) == lag[matched])
            {
                matched++;
            }

            diverged = matched < length;

            if (!diverged)
            {
                System.arraycopy(lag, matched, lag, 0, lagLength - matched);
                lagLength -= matched;
            }
        }

        return matched;
    }
}
