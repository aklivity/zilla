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
package io.aklivity.zilla.runtime.common.json.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * A {@link CharSequence} over the bytes of a JSON string inside the document that an escaped scope reads out of a
 * string, so the bytes are the string as it is written between its quotes, escaped twice: once by the string that
 * holds the document, and once by the string within the document. The chars are read in place, decoding both
 * layers as they are reached, and nothing is copied or allocated.
 * <p>
 * The chars are read in order, and a char just read can be read again. Reading further back starts again from the
 * start of the value. It is not a {@link io.aklivity.zilla.runtime.common.json.JsonStringView}: the bytes of a
 * string within a scope carry the outer escapes, so they are not bytes a caller can copy as the string's own, and
 * a caller reads the chars.
 * <p>
 * The view is a flyweight that is reused for every value and is valid only while the bytes it wraps are unchanged.
 * It assumes the bytes are a valid string body, as the tokenizer has already checked them.
 */
final class EscapedStringView implements CharSequence
{
    private DirectBufferEx buffer;
    private int offset;
    private int limit;
    private int length;

    private int cursorChar;
    private int unitByte;
    private int position;
    private char pending;
    private char previous;
    private long unit;
    private int unitIndex;
    private int unitCount;

    EscapedStringView wrap(
        DirectBufferEx buffer,
        int offset,
        int byteLength,
        int chars)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.limit = offset + byteLength;
        this.length = chars;

        restart();

        return this;
    }

    @Override
    public int length()
    {
        return length;
    }

    @Override
    public char charAt(
        int index)
    {
        if (index < 0 || index >= length)
        {
            throw new IndexOutOfBoundsException(index);
        }

        if (index < cursorChar - 1)
        {
            restart();
        }

        while (cursorChar <= index)
        {
            decodeNext();
        }

        return previous;
    }

    @Override
    public CharSequence subSequence(
        int start,
        int end)
    {
        if (start < 0 || end > length || start > end)
        {
            throw new IndexOutOfBoundsException(start + ", " + end);
        }

        final StringBuilder chars = new StringBuilder(end - start);

        for (int index = start; index < end; index++)
        {
            chars.append(charAt(index));
        }

        return chars.toString();
    }

    @Override
    public String toString()
    {
        return subSequence(0, length).toString();
    }

    /**
     * The offset in the buffer of the unit that holds the char at {@code charIndex}: where the escape or the
     * character begins, and the start of the pair when the char is the second half of a surrogate pair.
     */
    int floorOffsetOf(
        int charIndex)
    {
        restart();

        while (cursorChar <= charIndex)
        {
            decodeNext();
        }

        return unitByte;
    }

    private void restart()
    {
        cursorChar = 0;
        unitByte = offset;
        position = offset;
        pending = 0;
        previous = 0;
        unitIndex = 0;
        unitCount = 0;
    }

    private void decodeNext()
    {
        if (pending != 0)
        {
            previous = pending;
            pending = 0;
        }
        else
        {
            unitByte = position;

            final int b = inner();

            if (b == '\\')
            {
                previous = escape();
            }
            else if (b < 0x80)
            {
                previous = (char) b;
            }
            else
            {
                final int codePoint = sequence(b);

                if (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT)
                {
                    previous = Character.highSurrogate(codePoint);
                    pending = Character.lowSurrogate(codePoint);
                }
                else
                {
                    previous = (char) codePoint;
                }
            }
        }

        cursorChar++;
    }

    private char escape()
    {
        final int e = inner();
        final char c;

        if (e == 'u')
        {
            c = (char) (hex(inner()) << 12 | hex(inner()) << 8 | hex(inner()) << 4 | hex(inner()));
        }
        else
        {
            c = switch (e)
            {
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> (char) e;
            };
        }

        return c;
    }

    private int sequence(
        int lead)
    {
        final int continuations = lead < 0xe0 ? 1 : lead < 0xf0 ? 2 : 3;

        int codePoint = lead & (0x3f >> continuations);

        for (int continuation = 0; continuation < continuations; continuation++)
        {
            codePoint = codePoint << 6 | inner() & 0x3f;
        }

        return codePoint;
    }

    // the next byte of the string within the document, which is the next byte of the string as it is written, or a
    // byte of the escape it spells
    private int inner()
    {
        final int b;

        if (unitIndex < unitCount)
        {
            b = JsonEscapeLens.byteAt(unit, unitIndex++) & 0xff;
        }
        else
        {
            final int c = buffer.getByte(position) & 0xff;

            if (c != '\\')
            {
                position++;
                b = c;
            }
            else
            {
                unit = JsonEscapeLens.decode(buffer, position, limit, true);
                position += JsonEscapeLens.size(unit);
                unitCount = JsonEscapeLens.count(unit);
                unitIndex = 1;
                b = JsonEscapeLens.byteAt(unit, 0) & 0xff;
            }
        }

        return b;
    }

    private static int hex(
        int c)
    {
        return c <= '9' ? c - '0' : (c | 0x20) - 'a' + 10;
    }
}
