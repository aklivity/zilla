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
package io.aklivity.zilla.runtime.common.json.view;

import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Reads the bytes of a JSON string body as the UTF-8 bytes of the text it encodes, one unit at a time and without
 * a copy, so the document that string holds can be tokenized straight from the source.
 * <p>
 * A unit is one source byte, or one escape: a simple escape ({@code \n}) is one byte, a unicode escape is the
 * UTF-8 form of its code point, and the two escapes of a surrogate pair are the four bytes of one code point. A
 * lone surrogate is replaced by U+FFFD. A unit is returned packed in a long, so reading allocates nothing: its
 * bytes in the low half, the number of bytes at bit 32, and the number of source bytes it spans at bit 40.
 */
public final class JsonEscapeLens
{
    public static final long NEEDS_MORE = -1L;

    private static final int REPLACEMENT = 0xfffd;
    private static final int UNICODE_ESCAPE = 6;
    private static final int PAIR_ESCAPE = 12;
    private static final int COUNT_SHIFT = 32;
    private static final int SIZE_SHIFT = 40;
    private static final long MASK = 0xffL;

    private JsonEscapeLens()
    {
    }

    public static int count(
        long unit)
    {
        return (int) (unit >>> COUNT_SHIFT & MASK);
    }

    public static int size(
        long unit)
    {
        return (int) (unit >>> SIZE_SHIFT & MASK);
    }

    public static byte byteAt(
        long unit,
        int index)
    {
        return (byte) (unit >>> index * Byte.SIZE);
    }

    /**
     * The unit that starts at {@code offset}, or {@link #NEEDS_MORE} when the window ends before the unit is
     * complete and {@code last} says no more bytes follow it.
     */
    public static long decode(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        final int c = buffer.getByte(offset) & 0xff;

        return c == '\\' ? escape(buffer, offset, limit, last) : pack(c, 1, 1);
    }

    private static long escape(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        long unit = NEEDS_MORE;

        if (offset + 1 < limit)
        {
            final int e = buffer.getByte(offset + 1) & 0xff;

            unit = switch (e)
            {
            case '"', '\\', '/' -> pack(e, 1, 2);
            case 'b' -> pack('\b', 1, 2);
            case 'f' -> pack('\f', 1, 2);
            case 'n' -> pack('\n', 1, 2);
            case 'r' -> pack('\r', 1, 2);
            case 't' -> pack('\t', 1, 2);
            case 'u' -> unicode(buffer, offset, limit, last);
            default -> throw new JsonParsingException("Invalid escape: \\" + (char) e, null);
            };
        }

        return unit;
    }

    private static long unicode(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        long unit = NEEDS_MORE;

        if (offset + UNICODE_ESCAPE <= limit)
        {
            final int codeUnit = hex(buffer, offset);

            if (Character.isHighSurrogate((char) codeUnit))
            {
                unit = pair(buffer, offset, limit, last, codeUnit);
            }
            else if (Character.isLowSurrogate((char) codeUnit))
            {
                unit = utf8(REPLACEMENT, UNICODE_ESCAPE);
            }
            else
            {
                unit = utf8(codeUnit, UNICODE_ESCAPE);
            }
        }

        return unit;
    }

    private static long pair(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last,
        int high)
    {
        final int next = offset + UNICODE_ESCAPE;
        final int visible = limit - next;

        long unit = NEEDS_MORE;

        if (visible > 0 && buffer.getByte(next) != '\\' || visible > 1 && buffer.getByte(next + 1) != 'u')
        {
            unit = utf8(REPLACEMENT, UNICODE_ESCAPE);
        }
        else if (visible >= UNICODE_ESCAPE)
        {
            final int low = hex(buffer, next);

            unit = Character.isLowSurrogate((char) low)
                ? utf8(Character.toCodePoint((char) high, (char) low), PAIR_ESCAPE)
                : utf8(REPLACEMENT, UNICODE_ESCAPE);
        }
        else if (last)
        {
            unit = utf8(REPLACEMENT, UNICODE_ESCAPE);
        }

        return unit;
    }

    private static int hex(
        DirectBufferEx buffer,
        int offset)
    {
        int value = 0;

        for (int digit = 2; digit < UNICODE_ESCAPE; digit++)
        {
            final int c = buffer.getByte(offset + digit);
            final int nibble = c >= '0' && c <= '9' ? c - '0'
                : c >= 'a' && c <= 'f' ? c - 'a' + 10
                : c >= 'A' && c <= 'F' ? c - 'A' + 10
                : -1;

            if (nibble < 0)
            {
                throw new JsonParsingException("Invalid unicode escape digit: " + (char) c, null);
            }

            value = value << 4 | nibble;
        }

        return value;
    }

    private static long utf8(
        int codePoint,
        int size)
    {
        final long bytes;
        final int count;

        if (codePoint < 0x80)
        {
            bytes = codePoint;
            count = 1;
        }
        else if (codePoint < 0x800)
        {
            bytes = 0xc0 | codePoint >> 6 | (0x80 | codePoint & 0x3f) << 8;
            count = 2;
        }
        else if (codePoint < 0x10000)
        {
            bytes = 0xe0 | codePoint >> 12 | (0x80 | codePoint >> 6 & 0x3f) << 8 | (0x80 | codePoint & 0x3f) << 16;
            count = 3;
        }
        else
        {
            bytes = 0xf0 | codePoint >> 18 | (0x80 | codePoint >> 12 & 0x3f) << 8 |
                (0x80 | codePoint >> 6 & 0x3f) << 16 | (long) (0x80 | codePoint & 0x3f) << 24;
            count = 4;
        }

        return pack(bytes, count, size);
    }

    private static long pack(
        long bytes,
        int count,
        int size)
    {
        return bytes & 0xffffffffL | (long) count << COUNT_SHIFT | (long) size << SIZE_SHIFT;
    }
}
