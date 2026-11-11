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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.CharSequenceEx;

/**
 * A {@link CharSequenceEx} over the raw UTF-8 bytes of a string in a buffer, as bytes that carry no escapes, so the
 * chars are read in place and nothing is copied or allocated.
 * <p>
 * A transfer hands over the bytes of the chars as they are, raw UTF-8 with no escapes, so a writer that needs an
 * encoding of its own reads the chars and encodes them itself.
 * <p>
 * Bytes that are not well-formed UTF-8 read as U+FFFD, a char for each byte that is not part of a well-formed
 * sequence.
 * <p>
 * The chars are read in order, and a char just read can be read again. Reading further back starts again from the
 * start of the value. The view is a flyweight that is reused for every value and is valid only while the bytes it
 * wraps are unchanged.
 */
public final class Utf8StringView implements CharSequenceEx
{
    private static final char REPLACEMENT = '�';

    private DirectBufferEx buffer;
    private int offset;
    private int limit;
    private int length;
    private boolean ascii;

    private int cursorChar;
    private int position;
    private char pending;
    private char previous;

    public Utf8StringView wrap(
        DirectBufferEx buffer,
        int offset,
        int byteLength)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.limit = offset + byteLength;

        scan();

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

        final char c;

        if (ascii)
        {
            c = (char) buffer.getByte(offset + index);
        }
        else
        {
            if (index < cursorChar - 1)
            {
                restart();
            }

            while (cursorChar <= index)
            {
                decodeNext();
            }

            c = previous;
        }

        return c;
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

        final CharSequence chars;

        if (ascii)
        {
            chars = buffer.getStringWithoutLengthUtf8(offset + start, end - start);
        }
        else
        {
            final StringBuilder builder = new StringBuilder(end - start);

            for (int index = start; index < end; index++)
            {
                builder.append(charAt(index));
            }

            chars = builder.toString();
        }

        return chars;
    }

    @Override
    public String toString()
    {
        return subSequence(0, length).toString();
    }

    @Override
    public int transferLength(
        int fromChar,
        int toChar)
    {
        check(fromChar, toChar);

        return boundary(toChar) - boundary(fromChar);
    }

    @Override
    public int transferTo(
        int fromChar,
        int toChar,
        TransferTarget target)
    {
        check(fromChar, toChar);

        final int start = boundary(fromChar);
        final int end = boundary(toChar);

        if (end > start)
        {
            target.accept(buffer.segment(), buffer.wrapAdjustment() + start, end - start);
        }

        return end - start;
    }

    private void check(
        int fromChar,
        int toChar)
    {
        if (fromChar < 0 || toChar > length || fromChar > toChar)
        {
            throw new IndexOutOfBoundsException(fromChar + ", " + toChar);
        }
    }

    private void scan()
    {
        boolean ascii = true;

        for (int index = offset; ascii && index < limit; index++)
        {
            ascii = buffer.getByte(index) >= 0;
        }

        this.ascii = ascii;

        if (ascii)
        {
            this.length = limit - offset;
        }
        else
        {
            restart();

            while (position < limit || pending != 0)
            {
                decodeNext();
            }

            this.length = cursorChar;
            restart();
        }
    }

    private int boundary(
        int charIndex)
    {
        final int boundary;

        if (ascii)
        {
            boundary = offset + charIndex;
        }
        else
        {
            if (charIndex < cursorChar)
            {
                restart();
            }

            while (cursorChar < charIndex)
            {
                decodeNext();
            }

            if (pending != 0)
            {
                throw new IllegalArgumentException("index inside a character: " + charIndex);
            }

            boundary = cursorChar == 0 ? offset : position;
        }

        return boundary;
    }

    private void restart()
    {
        cursorChar = 0;
        position = offset;
        pending = 0;
        previous = 0;
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
            final int lead = buffer.getByte(position) & 0xff;
            final int continuations = continuations(lead);

            if (continuations == 0)
            {
                previous = (char) lead;
                position++;
            }
            else if (continuations < 0 || !continued(position + 1, continuations))
            {
                previous = REPLACEMENT;
                position++;
            }
            else
            {
                int codePoint = lead & (0x3f >> continuations);

                for (int continuation = 1; continuation <= continuations; continuation++)
                {
                    codePoint = codePoint << 6 | buffer.getByte(position + continuation) & 0x3f;
                }

                position += continuations + 1;

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

    private static int continuations(
        int lead)
    {
        final int continuations;

        if (lead < 0x80)
        {
            continuations = 0;
        }
        else if (lead < 0xc2 || lead >= 0xf5)
        {
            continuations = -1;
        }
        else
        {
            continuations = lead < 0xe0 ? 1 : lead < 0xf0 ? 2 : 3;
        }

        return continuations;
    }

    private boolean continued(
        int from,
        int count)
    {
        boolean continued = from + count <= limit;

        for (int index = from; continued && index < from + count; index++)
        {
            continued = (buffer.getByte(index) & 0xc0) == 0x80;
        }

        return continued;
    }
}
