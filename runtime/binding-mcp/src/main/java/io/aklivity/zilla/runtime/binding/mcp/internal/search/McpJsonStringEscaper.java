/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import java.nio.charset.StandardCharsets;

/**
 * Escapes a byte range for embedding as the body of a JSON string, without re-parsing or
 * re-serializing the source -- a straight byte scan doubling {@code "}/{@code \} and escaping
 * control bytes below {@code 0x20} as a six-byte backslash-u escape (backslash, u, 0, 0, then two
 * hex digits).
 */
public final class McpJsonStringEscaper
{
    private static final byte[] HEX_DIGITS = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    // the six-byte backslash-u escape is the widest expansion a single source byte can produce
    private static final int MAX_EXPANSION_PER_BYTE = 6;

    private McpJsonStringEscaper()
    {
    }

    public static int escape(
        byte[] src,
        int srcOffset,
        int srcLength,
        byte[] dst,
        int dstOffset)
    {
        int produced = dstOffset;
        for (int i = 0; i < srcLength; i++)
        {
            final byte value = src[srcOffset + i];
            if (value == '"' || value == '\\')
            {
                dst[produced++] = '\\';
                dst[produced++] = value;
            }
            else if (value >= 0x00 && value < 0x20)
            {
                dst[produced++] = '\\';
                dst[produced++] = 'u';
                dst[produced++] = '0';
                dst[produced++] = '0';
                dst[produced++] = HEX_DIGITS[(value >> 4) & 0xF];
                dst[produced++] = HEX_DIGITS[value & 0xF];
            }
            else
            {
                dst[produced++] = value;
            }
        }
        return produced - dstOffset;
    }

    public static int maxEscapedLength(
        int srcLength)
    {
        return srcLength * MAX_EXPANSION_PER_BYTE;
    }
}
