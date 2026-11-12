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

import java.lang.foreign.MemorySegment;

import io.aklivity.zilla.runtime.common.lang.CharSequenceEx.TransferTarget;

/**
 * Transfers the chars of a {@link JsonStringView} as the single-level body of a JSON string: a view whose bytes are
 * already that form is handed over as they are, and the chars of any other are encoded in UTF-8, with a quote, a
 * backslash and a control char escaped, and a lone surrogate, which has no UTF-8 form, written as U+FFFD.
 * <p>
 * The writer is a flyweight that is reused for every transfer and holds the bytes of an encoded transfer in an array
 * it grows to the largest it has been asked for.
 */
public final class JsonStringBody
{
    private static final int REPLACEMENT = 0xfffd;
    private static final byte[] HEX = "0123456789abcdef".getBytes();

    private byte[] bytes = new byte[64];
    private MemorySegment segment = MemorySegment.ofArray(bytes);

    /**
     * The number of bytes that {@link #transfer} hands over for the chars from {@code fromChar} to {@code toChar}.
     */
    public int length(
        JsonStringView chars,
        int fromChar,
        int toChar)
    {
        final int length;

        if (chars.kind() == JsonStringView.Kind.ENCODED)
        {
            length = chars.transferLength(fromChar, toChar);
        }
        else
        {
            check(chars, fromChar, toChar);

            int total = 0;

            for (int index = fromChar; index < toChar; )
            {
                final int codePoint = codePointAt(chars, index, toChar);

                total += width(codePoint);
                index += codePoint >= 0x10000 ? 2 : 1;
            }

            length = total;
        }

        return length;
    }

    /**
     * Hands the chars from {@code fromChar} to {@code toChar} to {@code target} as the body of a JSON string.
     *
     * @return the number of bytes handed over
     */
    public int transfer(
        JsonStringView chars,
        int fromChar,
        int toChar,
        TransferTarget target)
    {
        final int transferred;

        if (chars.kind() == JsonStringView.Kind.ENCODED)
        {
            transferred = chars.transferTo(fromChar, toChar, target);
        }
        else
        {
            final int length = length(chars, fromChar, toChar);

            if (length > bytes.length)
            {
                bytes = new byte[Math.max(length, bytes.length << 1)];
                segment = MemorySegment.ofArray(bytes);
            }

            int position = 0;

            for (int index = fromChar; index < toChar; )
            {
                final int codePoint = codePointAt(chars, index, toChar);

                position = put(codePoint, position);
                index += codePoint >= 0x10000 ? 2 : 1;
            }

            target.accept(segment, 0, position);

            transferred = position;
        }

        return transferred;
    }

    private static void check(
        CharSequence chars,
        int fromChar,
        int toChar)
    {
        if (fromChar < 0 || toChar > chars.length() || fromChar > toChar)
        {
            throw new IndexOutOfBoundsException(fromChar + ", " + toChar);
        }

        assert !splitsPair(chars, fromChar, toChar) : "index inside a surrogate pair: " + fromChar + ", " + toChar;
    }

    private static boolean splitsPair(
        CharSequence chars,
        int fromChar,
        int toChar)
    {
        return fromChar > 0 && fromChar < chars.length() &&
            Character.isLowSurrogate(chars.charAt(fromChar)) && Character.isHighSurrogate(chars.charAt(fromChar - 1)) ||
            toChar > 0 && toChar < chars.length() &&
            Character.isHighSurrogate(chars.charAt(toChar - 1)) && Character.isLowSurrogate(chars.charAt(toChar));
    }

    private static boolean isPair(
        CharSequence chars,
        int index,
        int limit)
    {
        return Character.isHighSurrogate(chars.charAt(index)) && index + 1 < limit &&
            Character.isLowSurrogate(chars.charAt(index + 1));
    }

    private static int codePointAt(
        CharSequence chars,
        int index,
        int limit)
    {
        final char c = chars.charAt(index);
        final int codePoint;

        if (isPair(chars, index, limit))
        {
            codePoint = Character.toCodePoint(c, chars.charAt(index + 1));
        }
        else if (Character.isSurrogate(c))
        {
            codePoint = REPLACEMENT;
        }
        else
        {
            codePoint = c;
        }

        return codePoint;
    }

    private static int width(
        int codePoint)
    {
        final int width;

        if (shortEscape(codePoint) != 0)
        {
            width = 2;
        }
        else if (codePoint < 0x20)
        {
            width = 6;
        }
        else if (codePoint < 0x80)
        {
            width = 1;
        }
        else if (codePoint < 0x800)
        {
            width = 2;
        }
        else if (codePoint < 0x10000)
        {
            width = 3;
        }
        else
        {
            width = 4;
        }

        return width;
    }

    private static int shortEscape(
        int codePoint)
    {
        return switch (codePoint)
        {
        case '"' -> '"';
        case '\\' -> '\\';
        case '\b' -> 'b';
        case '\f' -> 'f';
        case '\n' -> 'n';
        case '\r' -> 'r';
        case '\t' -> 't';
        default -> 0;
        };
    }

    private int put(
        int codePoint,
        int position)
    {
        int next = position;

        final int escape = shortEscape(codePoint);

        if (escape != 0)
        {
            bytes[next++] = '\\';
            bytes[next++] = (byte) escape;
        }
        else if (codePoint < 0x20)
        {
            bytes[next++] = '\\';
            bytes[next++] = 'u';
            bytes[next++] = '0';
            bytes[next++] = '0';
            bytes[next++] = HEX[codePoint >> 4];
            bytes[next++] = HEX[codePoint & 0xf];
        }
        else if (codePoint < 0x80)
        {
            bytes[next++] = (byte) codePoint;
        }
        else if (codePoint < 0x800)
        {
            bytes[next++] = (byte) (0xc0 | codePoint >> 6);
            bytes[next++] = (byte) (0x80 | codePoint & 0x3f);
        }
        else if (codePoint < 0x10000)
        {
            bytes[next++] = (byte) (0xe0 | codePoint >> 12);
            bytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3f);
            bytes[next++] = (byte) (0x80 | codePoint & 0x3f);
        }
        else
        {
            bytes[next++] = (byte) (0xf0 | codePoint >> 18);
            bytes[next++] = (byte) (0x80 | codePoint >> 12 & 0x3f);
            bytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3f);
            bytes[next++] = (byte) (0x80 | codePoint & 0x3f);
        }

        return next;
    }
}
