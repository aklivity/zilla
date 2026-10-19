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
package io.aklivity.zilla.runtime.common.lang;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Allocation-free inspection of {@link MemorySegment} contents, for callers that need to examine
 * encoded text in place rather than copying it out to a {@code String} or byte array.
 */
public final class MemorySegments
{
    private MemorySegments()
    {
    }

    /**
     * The number of bytes from {@code offset} that form complete, well-formed UTF-8 characters, found
     * without allocating or copying, so text that arrives in fragments can be validated as it goes.
     * Characters must use the shortest encoding, may not be surrogates and may not exceed U+10FFFF.
     * When the count is less than {@code length}, the remaining bytes, at most three, are the start of
     * a character that the end of the range cuts short and are valid so far; a caller that holds them
     * back can check them again together with the next fragment. A count equal to {@code length}
     * means the whole range is well-formed.
     *
     * @return the length of the complete characters, or {@code -1} if the bytes are malformed
     */
    public static int utf8Bytes(
        MemorySegment segment,
        long offset,
        long length)
    {
        final long limit = offset + length;
        long index = offset;
        boolean malformed = false;
        boolean truncated = false;

        while (!malformed && !truncated && index < limit)
        {
            final int lead = segment.get(ValueLayout.JAVA_BYTE, index) & 0xff;
            final int trailing = utf8TrailingLength(lead);
            final long end = index + trailing;

            malformed = trailing < 0;

            for (long next = index + 1; !malformed && next <= end && next < limit; next++)
            {
                final int trail = segment.get(ValueLayout.JAVA_BYTE, next) & 0xff;

                malformed = trail < (next == index + 1 ? utf8SecondByteMin(lead) : 0x80) ||
                    trail > (next == index + 1 ? utf8SecondByteMax(lead) : 0xbf);
            }

            truncated = !malformed && end >= limit;
            index = truncated ? index : end + 1;
        }

        return malformed ? -1 : (int) (index - offset);
    }

    private static int utf8TrailingLength(
        int lead)
    {
        final int length;

        if (lead < 0x80)
        {
            length = 0;
        }
        else if (lead >= 0xc2 && lead <= 0xdf)
        {
            length = 1;
        }
        else if (lead >= 0xe0 && lead <= 0xef)
        {
            length = 2;
        }
        else if (lead >= 0xf0 && lead <= 0xf4)
        {
            length = 3;
        }
        else
        {
            length = -1;
        }

        return length;
    }

    private static int utf8SecondByteMin(
        int lead)
    {
        return lead == 0xe0 ? 0xa0 : lead == 0xf0 ? 0x90 : 0x80;
    }

    private static int utf8SecondByteMax(
        int lead)
    {
        return lead == 0xed ? 0x9f : lead == 0xf4 ? 0x8f : 0xbf;
    }
}
