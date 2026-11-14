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

final class LlmJsonEscape
{
    static final int EXPANSION = 6;

    private static final long ONES = 0x0101010101010101L;
    private static final long HIGH_BITS = 0x8080808080808080L;
    private static final long CONTROLS = ONES * 0x20;
    private static final long QUOTES = ONES * '"';
    private static final long BACKSLASHES = ONES * '\\';

    private static final byte[] HEX = "0123456789abcdef".getBytes();

    static int escape(
        DirectBufferEx source,
        int offset,
        int length,
        byte[] target)
    {
        int position = 0;
        int index = 0;

        while (index < length)
        {
            final int plain = plainBytes(source, offset + index, length - index);

            if (plain > 0)
            {
                source.getBytes(offset + index, target, position, plain);
                position += plain;
                index += plain;
                continue;
            }

            final byte b = source.getByte(offset + index);
            index++;

            if (b == '"' || b == '\\')
            {
                target[position++] = '\\';
                target[position++] = b;
            }
            else if (b >= 0 && b < 0x20)
            {
                position = escapeControl(b, target, position);
            }
            else
            {
                target[position++] = b;
            }
        }

        return position;
    }

    private static int plainBytes(
        DirectBufferEx source,
        int offset,
        int length)
    {
        int plain = 0;

        while (plain + Long.BYTES <= length && plainWord(source.getLong(offset + plain)))
        {
            plain += Long.BYTES;
        }

        return plain;
    }

    private static boolean plainWord(
        long word)
    {
        final long quotes = word ^ QUOTES;
        final long slashes = word ^ BACKSLASHES;
        final long found = ((word - CONTROLS) & ~word | (quotes - ONES) & ~quotes | (slashes - ONES) & ~slashes) & HIGH_BITS;

        return found == 0L;
    }

    static int escapedLength(
        DirectBufferEx source,
        int offset,
        int length)
    {
        int escaped = 0;

        for (int index = 0; index < length; index++)
        {
            final byte b = source.getByte(offset + index);

            if (b == '"' || b == '\\')
            {
                escaped += 2;
            }
            else if (b >= 0 && b < 0x20)
            {
                escaped += b == '\n' || b == '\r' || b == '\t' || b == '\b' || b == '\f' ? 2 : EXPANSION;
            }
            else
            {
                escaped++;
            }
        }

        return escaped;
    }

    private static int escapeControl(
        byte b,
        byte[] target,
        int position)
    {
        int next = position;

        target[next++] = '\\';

        switch (b)
        {
        case '\n':
            target[next++] = 'n';
            break;
        case '\r':
            target[next++] = 'r';
            break;
        case '\t':
            target[next++] = 't';
            break;
        case '\b':
            target[next++] = 'b';
            break;
        case '\f':
            target[next++] = 'f';
            break;
        default:
            target[next++] = 'u';
            target[next++] = '0';
            target[next++] = '0';
            target[next++] = HEX[b >> 4];
            target[next++] = HEX[b & 0xF];
            break;
        }

        return next;
    }

    private LlmJsonEscape()
    {
    }
}
