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

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public final class AguiJsonEscape
{
    public static final int MALFORMED = -1;

    private static final int MAX_ESCAPED_WIDTH = 6;
    private static final byte[] HEX_DIGITS = "0123456789abcdef".getBytes(UTF_8);

    public static int growth()
    {
        return MAX_ESCAPED_WIDTH;
    }

    public static int escapedWidth(
        int octet)
    {
        final int width;

        switch (octet)
        {
        case '"':
        case '\\':
        case '\b':
        case '\f':
        case '\n':
        case '\r':
        case '\t':
            width = 2;
            break;
        default:
            width = octet >= 0 && octet < 0x20 ? MAX_ESCAPED_WIDTH : 1;
            break;
        }

        return width;
    }

    public static int escape(
        DirectBufferEx source,
        int offset,
        int limit,
        MutableDirectBufferEx target,
        int index)
    {
        int progress = index;

        for (int position = offset; position < limit; position++)
        {
            final byte octet = source.getByte(position);

            switch (octet)
            {
            case '"':
                progress = escapeSimple(target, progress, '"');
                break;
            case '\\':
                progress = escapeSimple(target, progress, '\\');
                break;
            case '\b':
                progress = escapeSimple(target, progress, 'b');
                break;
            case '\f':
                progress = escapeSimple(target, progress, 'f');
                break;
            case '\n':
                progress = escapeSimple(target, progress, 'n');
                break;
            case '\r':
                progress = escapeSimple(target, progress, 'r');
                break;
            case '\t':
                progress = escapeSimple(target, progress, 't');
                break;
            default:
                if (octet >= 0 && octet < 0x20)
                {
                    target.putByte(progress++, (byte) '\\');
                    target.putByte(progress++, (byte) 'u');
                    target.putByte(progress++, (byte) '0');
                    target.putByte(progress++, (byte) '0');
                    target.putByte(progress++, HEX_DIGITS[octet >> 4]);
                    target.putByte(progress++, HEX_DIGITS[octet & 0xf]);
                }
                else
                {
                    target.putByte(progress++, octet);
                }
                break;
            }
        }

        return progress;
    }

    public static int completeLength(
        DirectBufferEx source,
        int offset,
        int limit)
    {
        int progress = offset;
        int complete = 0;

        while (complete != MALFORMED && progress < limit)
        {
            final int lead = source.getByte(progress) & 0xff;
            final int width = sequenceWidth(lead);

            if (width == 0)
            {
                complete = MALFORMED;
            }
            else
            {
                final int available = Math.min(width, limit - progress);

                boolean continuations = true;
                for (int index = 1; continuations && index < available; index++)
                {
                    continuations = (source.getByte(progress + index) & 0xc0) == 0x80;
                }

                if (!continuations)
                {
                    complete = MALFORMED;
                }
                else if (available < width)
                {
                    progress = limit;
                }
                else
                {
                    progress += width;
                    complete = progress - offset;
                }
            }
        }

        return complete;
    }

    public static String escape(
        String value)
    {
        final StringBuilder escaped = new StringBuilder(value.length() + 8);

        for (int index = 0; index < value.length(); index++)
        {
            final char ch = value.charAt(index);

            switch (ch)
            {
            case '"':
                escaped.append("\\\"");
                break;
            case '\\':
                escaped.append("\\\\");
                break;
            case '\b':
                escaped.append("\\b");
                break;
            case '\f':
                escaped.append("\\f");
                break;
            case '\n':
                escaped.append("\\n");
                break;
            case '\r':
                escaped.append("\\r");
                break;
            case '\t':
                escaped.append("\\t");
                break;
            default:
                if (ch < 0x20)
                {
                    escaped.append(String.format("\\u%04x", (int) ch));
                }
                else
                {
                    escaped.append(ch);
                }
                break;
            }
        }

        return escaped.toString();
    }

    private static int escapeSimple(
        MutableDirectBufferEx target,
        int index,
        char escaped)
    {
        target.putByte(index, (byte) '\\');
        target.putByte(index + 1, (byte) escaped);

        return index + 2;
    }

    private static int sequenceWidth(
        int lead)
    {
        final int width;

        if (lead < 0x80)
        {
            width = 1;
        }
        else if ((lead & 0xe0) == 0xc0)
        {
            width = 2;
        }
        else if ((lead & 0xf0) == 0xe0)
        {
            width = 3;
        }
        else if ((lead & 0xf8) == 0xf0)
        {
            width = 4;
        }
        else
        {
            width = 0;
        }

        return width;
    }

    private AguiJsonEscape()
    {
    }
}
