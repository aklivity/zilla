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
package io.aklivity.zilla.runtime.binding.agui.internal.codec;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public final class AguiJsonUnescape
{
    public static final int MALFORMED = -1;

    private static final int ESCAPE_WIDTH = 6;
    private static final int HIGH_SURROGATE_MIN = 0xd800;
    private static final int HIGH_SURROGATE_MAX = 0xdbff;
    private static final int LOW_SURROGATE_MIN = 0xdc00;
    private static final int LOW_SURROGATE_MAX = 0xdfff;

    public static int completeLength(
        DirectBufferEx source,
        int offset,
        int limit)
    {
        int progress = offset;
        int width = 1;

        while (width > 0 && progress < limit)
        {
            width = tokenWidth(source, progress, limit);

            if (width > 0)
            {
                progress += width;
            }
        }

        return width == MALFORMED ? MALFORMED : progress - offset;
    }

    public static int unescape(
        DirectBufferEx source,
        int offset,
        int limit,
        MutableDirectBufferEx target,
        int index)
    {
        int progress = index;
        int position = offset;

        while (position < limit)
        {
            final byte octet = source.getByte(position);

            if (octet == '\\')
            {
                final byte escaped = source.getByte(position + 1);

                switch (escaped)
                {
                case 'b':
                    target.putByte(progress++, (byte) '\b');
                    position += 2;
                    break;
                case 'f':
                    target.putByte(progress++, (byte) '\f');
                    position += 2;
                    break;
                case 'n':
                    target.putByte(progress++, (byte) '\n');
                    position += 2;
                    break;
                case 'r':
                    target.putByte(progress++, (byte) '\r');
                    position += 2;
                    break;
                case 't':
                    target.putByte(progress++, (byte) '\t');
                    position += 2;
                    break;
                case 'u':
                    int codePoint = hex(source, position + 2);
                    position += ESCAPE_WIDTH;

                    if (codePoint >= HIGH_SURROGATE_MIN && codePoint <= HIGH_SURROGATE_MAX)
                    {
                        final int low = hex(source, position + 2) - LOW_SURROGATE_MIN;
                        codePoint = 0x10000 + ((codePoint - HIGH_SURROGATE_MIN) << 10) + low;
                        position += ESCAPE_WIDTH;
                    }

                    progress += target.putStringWithoutLengthUtf8(progress, new String(Character.toChars(codePoint)));
                    break;
                default:
                    target.putByte(progress++, escaped);
                    position += 2;
                    break;
                }
            }
            else
            {
                target.putByte(progress++, octet);
                position++;
            }
        }

        return progress;
    }

    private static int tokenWidth(
        DirectBufferEx source,
        int position,
        int limit)
    {
        final int lead = source.getByte(position) & 0xff;
        final int width;

        if (lead == '\\')
        {
            width = escapeWidth(source, position, limit);
        }
        else
        {
            width = sequenceWidth(source, position, limit, lead);
        }

        return width;
    }

    private static int escapeWidth(
        DirectBufferEx source,
        int position,
        int limit)
    {
        int width = 0;

        if (limit - position > 1)
        {
            switch (source.getByte(position + 1))
            {
            case '"':
            case '\\':
            case '/':
            case 'b':
            case 'f':
            case 'n':
            case 'r':
            case 't':
                width = 2;
                break;
            case 'u':
                width = unicodeWidth(source, position, limit);
                break;
            default:
                width = MALFORMED;
                break;
            }
        }

        return width;
    }

    private static int unicodeWidth(
        DirectBufferEx source,
        int position,
        int limit)
    {
        int width = 0;
        final int available = limit - position;

        boolean valid = true;
        for (int index = 2; valid && index < Math.min(ESCAPE_WIDTH, available); index++)
        {
            valid = digit(source.getByte(position + index)) >= 0;
        }

        if (!valid)
        {
            width = MALFORMED;
        }
        else if (available >= ESCAPE_WIDTH)
        {
            final int codePoint = hex(source, position + 2);

            if (codePoint >= LOW_SURROGATE_MIN && codePoint <= LOW_SURROGATE_MAX)
            {
                width = MALFORMED;
            }
            else if (codePoint >= HIGH_SURROGATE_MIN && codePoint <= HIGH_SURROGATE_MAX)
            {
                width = surrogatePairWidth(source, position, limit);
            }
            else
            {
                width = ESCAPE_WIDTH;
            }
        }

        return width;
    }

    private static int surrogatePairWidth(
        DirectBufferEx source,
        int position,
        int limit)
    {
        int width = 0;
        final int low = position + ESCAPE_WIDTH;
        final int available = limit - low;

        boolean valid = true;
        for (int index = 0; valid && index < Math.min(2, available); index++)
        {
            valid = source.getByte(low + index) == (index == 0 ? '\\' : 'u');
        }

        for (int index = 2; valid && index < Math.min(ESCAPE_WIDTH, available); index++)
        {
            valid = digit(source.getByte(low + index)) >= 0;
        }

        if (!valid)
        {
            width = MALFORMED;
        }
        else if (available >= ESCAPE_WIDTH)
        {
            final int codePoint = hex(source, low + 2);

            width = codePoint >= LOW_SURROGATE_MIN && codePoint <= LOW_SURROGATE_MAX ? 2 * ESCAPE_WIDTH : MALFORMED;
        }

        return width;
    }

    private static int sequenceWidth(
        DirectBufferEx source,
        int position,
        int limit,
        int lead)
    {
        final int expected;

        if (lead < 0x80)
        {
            expected = 1;
        }
        else if (lead >= 0xc2 && lead <= 0xdf)
        {
            expected = 2;
        }
        else if (lead >= 0xe0 && lead <= 0xef)
        {
            expected = 3;
        }
        else if (lead >= 0xf0 && lead <= 0xf4)
        {
            expected = 4;
        }
        else
        {
            expected = 0;
        }

        int width = expected == 0 ? MALFORMED : 0;

        if (expected != 0)
        {
            final int available = Math.min(expected, limit - position);

            boolean continuations = true;
            for (int index = 1; continuations && index < available; index++)
            {
                continuations = (source.getByte(position + index) & 0xc0) == 0x80;
            }

            if (!continuations)
            {
                width = MALFORMED;
            }
            else if (available == expected)
            {
                width = expected;
            }
        }

        return width;
    }

    private static int hex(
        DirectBufferEx source,
        int position)
    {
        int value = 0;

        for (int index = 0; index < 4; index++)
        {
            value = (value << 4) | digit(source.getByte(position + index));
        }

        return value;
    }

    private static int digit(
        byte octet)
    {
        final int digit;

        if (octet >= '0' && octet <= '9')
        {
            digit = octet - '0';
        }
        else if (octet >= 'a' && octet <= 'f')
        {
            digit = octet - 'a' + 10;
        }
        else if (octet >= 'A' && octet <= 'F')
        {
            digit = octet - 'A' + 10;
        }
        else
        {
            digit = -1;
        }

        return digit;
    }

    private AguiJsonUnescape()
    {
    }
}
