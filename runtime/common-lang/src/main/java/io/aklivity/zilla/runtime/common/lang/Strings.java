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
 * Allocation-free string measurement, for streaming and wire-encoding callers that need a
 * {@code String}'s encoded byte length without allocating the encoded array.
 */
public final class Strings
{
    private Strings()
    {
    }

    /**
     * The UTF-8 encoded byte length of {@code value}, computed by walking its UTF-16 code units and
     * applying UTF-8's encoding width per code point, rather than {@code value.getBytes(UTF_8).length}
     * (which allocates the encoded array just to read its length). An unpaired surrogate encodes as a
     * single byte, matching the one-byte {@code '?'} substitution {@code String.getBytes(UTF_8)} itself
     * falls back to for malformed input.
     */
    public static int utf8Length(
        CharSequence value)
    {
        int length = 0;
        int i = 0;
        final int n = value.length();
        while (i < n)
        {
            final char c = value.charAt(i);
            if (c < 0x80)
            {
                length += 1;
            }
            else if (c < 0x800)
            {
                length += 2;
            }
            else if (Character.isHighSurrogate(c))
            {
                if (i + 1 < n && Character.isLowSurrogate(value.charAt(i + 1)))
                {
                    length += 4;
                    i++;
                }
                else
                {
                    length += 1;
                }
            }
            else if (Character.isLowSurrogate(c))
            {
                length += 1;
            }
            else
            {
                length += 3;
            }
            i++;
        }
        return length;
    }

    /**
     * Whether the bytes of {@code segment} in {@code [offset, limit)} are well-formed UTF-8, checked
     * without allocating or copying: every sequence uses the shortest encoding, no code point is a
     * surrogate or beyond U+10FFFF, and no sequence is truncated by {@code limit}.
     */
    public static boolean utf8Valid(
        MemorySegment segment,
        long offset,
        long limit)
    {
        boolean valid = true;
        long index = offset;

        while (valid && index < limit)
        {
            final int lead = segment.get(ValueLayout.JAVA_BYTE, index) & 0xff;
            final int trailing = utf8TrailingLength(lead);

            valid = trailing >= 0 && index + trailing < limit;

            for (int next = 1; valid && next <= trailing; next++)
            {
                final int trail = segment.get(ValueLayout.JAVA_BYTE, index + next) & 0xff;

                valid = trail >= (next == 1 ? utf8SecondByteMin(lead) : 0x80) &&
                    trail <= (next == 1 ? utf8SecondByteMax(lead) : 0xbf);
            }

            index += trailing + 1;
        }

        return valid;
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
