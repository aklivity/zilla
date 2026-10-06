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
}
